package de.peeeq.wurstscript.translation.imoptimizer;

import com.google.common.collect.Lists;
import de.peeeq.wurstio.TimeTaker;
import de.peeeq.wurstscript.WurstOperator;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.intermediatelang.optimizer.BranchMerger;
import de.peeeq.wurstscript.intermediatelang.optimizer.ConstantAndCopyPropagation;
import de.peeeq.wurstscript.intermediatelang.optimizer.DispatchCheckDeduplicator;
import de.peeeq.wurstscript.intermediatelang.optimizer.LocalMerger;
import de.peeeq.wurstscript.intermediatelang.optimizer.SideEffectAnalyzer;
import de.peeeq.wurstscript.intermediatelang.optimizer.SimpleRewrites;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.types.TypesHelper;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.validation.TRVEHelper;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static de.peeeq.wurstscript.validation.TRVEHelper.TO_KEEP;

public class ImOptimizer {
    private int totalFunctionsRemoved = 0;
    private int totalGlobalsRemoved = 0;

    public static int localOptRounds = 1;
    private static final ArrayList<OptimizerPass> localPasses = new ArrayList<>();
    private static final HashMap<String, Integer> totalCount = new HashMap<>();
    private static final long LOCAL_PASS_HEARTBEAT_MS = Long.getLong("wurst.localopt.heartbeat.ms", 15000L);
    private static final int STRICT_INLINE_MAX_SIZE = Integer.getInteger("wurst.strictInline.maxSize", 8);
    private static final boolean STRICT_INLINE_PROFILE = Boolean.parseBoolean(System.getProperty("wurst.strictInline.profile", "true"));

    private static void logLocalOpt(String msg) {
        // Use warning-level so diagnostics are visible in environments that hide INFO logs.
        WLogger.warning("[LocalOpt] " + msg);
    }

    static {
        localPasses.add(new SimpleRewrites());
        localPasses.add(new LocalMerger());
        localPasses.add(new BranchMerger());
        localPasses.add(new ConstantAndCopyPropagation());
        localPasses.add(new UselessFunctionCallsRemover());
        localPasses.add(new GlobalsInliner());
        localPasses.add(new DispatchCheckDeduplicator());
        localPasses.add(new SimpleRewrites());
    }

    private final TimeTaker timeTaker;
    ImTranslator trans;

    public ImOptimizer(TimeTaker timeTaker, ImTranslator trans) {
        this.timeTaker = timeTaker;
        this.trans = trans;
    }

    public void optimize() {
        removeGarbage();
        ImCompressor compressor = new ImCompressor(trans);
        compressor.compressNames();
    }

    public void doInlining() {
        // remove garbage to reduce work for the inliner
        removeGarbage();
        GlobalsInliner globalsInliner = new GlobalsInliner();
        globalsInliner.optimize(trans);
        ImInliner inliner = new ImInliner(trans);
        inliner.doInlining();
        trans.assertProperties();
        // remove garbage, because inlined functions can be removed
        removeGarbage();
    }

    private int optCount = 1;

    public void localOptimizations() {
        totalCount.clear();

        logLocalOpt("initial cleanup start");
        removeGarbage();
        trans.getImProg().flatten(trans);
        logLocalOpt("initial cleanup done");

        int finalItr = 0;
        for (int i = 1; i <= localOptRounds && optCount > 0; i++) {
            optCount = 0;
            StringBuilder sb = new StringBuilder();
            logLocalOpt("round " + i + " start");
            for (OptimizerPass pass : localPasses) {
                int count = runLocalPassWithHeartbeat(pass, i);
                sb.append("<").append(pass.getName()).append(": ").append(count).append("> ");
                optCount += count;
                totalCount.put(pass.getName(), totalCount.getOrDefault(pass.getName(), 0) + count);
            }

            if (optCount > 0) {
                logLocalOpt("round " + i + ": cleanup after pass changes");
                removeGarbage();
                trans.getImProg().flatten(trans);
            }

            finalItr = i;
            logLocalOpt("round " + i + " done, opts=" + optCount);

            // Run a strict inliner to get rid of one-liners
            logLocalOpt("round " + i + ": strict inline start");
            doStrictInline();
            logLocalOpt("round " + i + ": strict inline done");
        }
        logLocalOpt("done, rounds=" + finalItr);
        StringBuilder sb = new StringBuilder("Total: ");
        totalCount.forEach((k, v) -> sb.append("<").append(k).append(": ").append(v).append("> "));
        logLocalOpt(sb.toString());

        InitFunctionCleaner.clean(trans.getImProg());
    }

    private int runLocalPassWithHeartbeat(OptimizerPass pass, int round) {
        final String passName = pass.getName();
        final long started = System.nanoTime();
        final AtomicBoolean running = new AtomicBoolean(true);
        Thread heartbeat = new Thread(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(LOCAL_PASS_HEARTBEAT_MS);
                } catch (InterruptedException e) {
                    return;
                }
                if (!running.get()) {
                    return;
                }
                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                logLocalOpt("round=" + round + " pass='" + passName + "' still running after " + elapsedMs + " ms");
            }
        }, "wurst-localopt-heartbeat-" + passName.replace(' ', '_'));
        heartbeat.setDaemon(true);

        logLocalOpt("round=" + round + " START pass='" + passName + "'");
        heartbeat.start();
        try {
            int count = timeTaker.measure(passName, () -> pass.optimize(trans));
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            logLocalOpt("round=" + round + " END pass='" + passName + "' count=" + count + " elapsedMs=" + elapsedMs);
            return count;
        } catch (Throwable t) {
            logLocalOpt("round=" + round + " FAIL pass='" + passName + "' after " + ((System.nanoTime() - started) / 1_000_000L) + " ms: " + t);
            throw t;
        } finally {
            running.set(false);
            heartbeat.interrupt();
        }
    }

    public void doNullsetting() {
        NullSetter ns = new NullSetter(trans);
        ns.optimize();
        trans.assertProperties();
    }

    public boolean removeGarbage() {
        boolean changes = true;
        boolean anyChanges = false;
        int iterations = 0;
        while (changes && iterations++ < 10) {
            ImProg prog = trans.imProg();
            trans.calculateCallRelationsAndReadVariables();
            final Set<ImVar> readVars = trans.getReadVariables();
            final Set<ImFunction> usedFuncs = trans.getUsedFunctions();
            SideEffectAnalyzer sideEffectAnalyzer = new SideEffectAnalyzer(prog);

            // keep only used variables
            int globalsBefore = prog.getGlobals().size();

            int globalsCount = prog.getGlobals().size();
            prog.getGlobals().removeIf(g ->
                !readVars.contains(g) && !TO_KEEP.contains(g.getName())
            );
            changes = prog.getGlobals().retainAll(readVars);
            int globalsAfter = prog.getGlobals().size();
            int globalsRemoved = globalsBefore - globalsAfter;
            totalGlobalsRemoved += globalsRemoved;

            // keep only functions reachable from main and config
            int functionsBefore = prog.getFunctions().size();
            changes |= prog.getFunctions().retainAll(usedFuncs);
            int functionsAfter = prog.getFunctions().size();
            int functionsRemoved = functionsBefore - functionsAfter;
            totalFunctionsRemoved += functionsRemoved;

            // also consider class functions
            Set<ImFunction> allFunctions = new HashSet<>(prog.getFunctions());
            for (ImClass c : prog.getClasses()) {
                int classFunctionsBefore = c.getFunctions().size();
                changes |= c.getFunctions().retainAll(usedFuncs);
                int classFunctionsAfter = c.getFunctions().size();
                totalFunctionsRemoved += classFunctionsBefore - classFunctionsAfter;
                allFunctions.addAll(c.getFunctions());

                int classFieldsBefore = c.getFields().size();
                changes |= c.getFields().retainAll(readVars);
                int classFieldsAfter = c.getFields().size();
                totalGlobalsRemoved += classFieldsBefore - classFieldsAfter;
            }

            for (ImFunction f : allFunctions) {
                // remove set statements to unread variables
                final List<Pair<ImStmt, List<ImExpr>>> replacements = Lists.newArrayList();
                f.accept(new ImFunction.DefaultVisitor() {
                    @Override
                    public void visit(ImSet e) {
                        super.visit(e);
                        if (e.getLeft() instanceof ImVarAccess) {
                            ImVarAccess va = (ImVarAccess) e.getLeft();
                            if (!readVars.contains(va.getVar()) && !TO_KEEP.contains(va.getVar().getName())) {
                                List<ImExpr> sideEffects = collectSideEffects(e.getRight(), sideEffectAnalyzer);
                                replacements.add(Pair.create(e, sideEffects));
                            }
                        } else if (e.getLeft() instanceof ImVarArrayAccess) {
                            ImVarArrayAccess va = (ImVarArrayAccess) e.getLeft();
                            if (!readVars.contains(va.getVar()) && !TO_KEEP.contains(va.getVar().getName())) {
                                List<ImExpr> exprs = new ArrayList<>();
                                for (ImExpr index : va.getIndexes()) {
                                    exprs.addAll(collectSideEffects(index, sideEffectAnalyzer));
                                }
                                exprs.addAll(collectSideEffects(e.getRight(), sideEffectAnalyzer));
                                replacements.add(Pair.create(e, exprs));
                            }
                        } else if (e.getLeft() instanceof ImTupleSelection) {
                            ImVar var = TypesHelper.getTupleVar((ImTupleSelection) e.getLeft());
                            if (var != null && !readVars.contains(var) && !TO_KEEP.contains(var.getName())) {
                                List<ImExpr> sideEffects = collectSideEffects(e.getRight(), sideEffectAnalyzer);
                                replacements.add(Pair.create(e, sideEffects));
                            }
                        } else if(e.getLeft() instanceof ImMemberAccess) {
                            ImMemberAccess va = ((ImMemberAccess) e.getLeft());
                            if (!readVars.contains(va.getVar()) && !TO_KEEP.contains(va.getVar().getName())) {
                                List<ImExpr> sideEffects = collectSideEffects(e.getRight(), sideEffectAnalyzer);
                                replacements.add(Pair.create(e, sideEffects));
                            }
                        }
                    }
                });

                Replacer replacer = new Replacer();
                for (Pair<ImStmt, List<ImExpr>> pair : replacements) {
                    changes = true;
                    ImExpr r;
                    if (pair.getB().isEmpty()) {
                        r = ImHelper.statementExprVoid(JassIm.ImStmts());
                    } else if (pair.getB().size() == 1) {
                        r = pair.getB().get(0);
                        // CRITICAL: Clear parent before reusing the node
                        r.setParent(null);
                    } else {
                        // CRITICAL: Create proper list wrapper for multiple expressions
                        List<ImStmt> stmts = new ArrayList<>();
                        for (ImExpr expr : pair.getB()) {
                            // Clear parent for each expression
                            expr.setParent(null);
                            stmts.add(expr);
                        }
                        r = ImHelper.statementExprVoid(JassIm.ImStmts(stmts));
                    }
                    replacer.replace(pair.getA(), r);
                }

                // keep only read local variables
                changes |= f.getLocals().retainAll(readVars);
            }
            anyChanges |= changes;
        }
        return anyChanges;
    }

    private List<ImExpr> collectSideEffects(ImExpr expr, SideEffectAnalyzer analyzer) {
        if (expr == null) {
            return Collections.emptyList();
        }
        if (mayTrapAtRuntime(expr)) {
            return Collections.singletonList(expr);
        }
        if (analyzer.hasObservableSideEffects(expr, func -> func.isNative()
            && UselessFunctionCallsRemover.isFunctionWithoutSideEffect(func.getName()))) {
            return Collections.singletonList(expr);
        }
        return Collections.emptyList();
    }

    private boolean mayTrapAtRuntime(Element elem) {
        return mayTrapAtRuntime(elem, new HashMap<>(), new LinkedHashSet<>());
    }

    private boolean mayTrapAtRuntime(Element elem, Map<ImFunction, Boolean> functionCache, Set<ImFunction> inProgress) {
        if (elem instanceof ImFunctionCall) {
            ImFunction calledFunc = ((ImFunctionCall) elem).getFunc();
            if (functionMayTrapAtRuntime(calledFunc, functionCache, inProgress)) {
                return true;
            }
        } else if (elem instanceof ImMethodCall) {
            ImFunction calledFunc = ((ImMethodCall) elem).getMethod().getImplementation();
            if (calledFunc == null || functionMayTrapAtRuntime(calledFunc, functionCache, inProgress)) {
                return true;
            }
        }

        if (elem instanceof ImOperatorCall) {
            ImOperatorCall opCall = (ImOperatorCall) elem;
            WurstOperator op = opCall.getOp();
            if ((op == WurstOperator.DIV_INT || op == WurstOperator.MOD_INT) && opCall.getArguments().size() >= 2) {
                ImExpr denominator = opCall.getArguments().get(1);
                // Preserve integer div/mod unless denominator is provably non-zero.
                if (!(denominator instanceof ImIntVal) || ((ImIntVal) denominator).getValI() == 0) {
                    return true;
                }
            }
        }
        for (int i = 0; i < elem.size(); i++) {
            Element child = elem.get(i);
            if (mayTrapAtRuntime(child, functionCache, inProgress)) {
                return true;
            }
        }
        return false;
    }

    private boolean functionMayTrapAtRuntime(ImFunction function, Map<ImFunction, Boolean> functionCache, Set<ImFunction> inProgress) {
        if (function.isNative()) {
            return false;
        }

        Boolean cachedResult = functionCache.get(function);
        if (cachedResult != null) {
            return cachedResult;
        }

        if (!inProgress.add(function)) {
            // Recursive cycles are conservatively treated as potentially trapping.
            return true;
        }

        boolean mayTrap = mayTrapAtRuntime(function.getBody(), functionCache, inProgress);
        inProgress.remove(function);
        functionCache.put(function, mayTrap);
        return mayTrap;
    }


    public void doStrictInline() {
        logLocalOpt("execute strict inline");
        if (STRICT_INLINE_PROFILE) {
            logImSizeSummary("strict inline BEFORE");
        }
        ImInliner inliner = new ImInliner(trans);
        inliner.setInlineTreshold(1);
        inliner.setAllowMultiReturnInlining(false);
        inliner.setMaxInlineFunctionSize(STRICT_INLINE_MAX_SIZE);
        logLocalOpt("strict inline config: threshold=1, maxSize=" + STRICT_INLINE_MAX_SIZE + ", multiReturn=false");
        inliner.doInlining();
        trans.assertProperties();
        removeGarbage();
        trans.getImProg().flatten(trans);
        if (STRICT_INLINE_PROFILE) {
            logImSizeSummary("strict inline AFTER");
        }
    }

    public void encryptStrings() {
        WLogger.info("encrypting strings");
        StringCryptor.encrypt(trans);
        removeGarbage();
        trans.getImProg().flatten(trans);
    }

    private void logImSizeSummary(String label) {
        List<ImFunction> functions = new ArrayList<>(ImHelper.calculateFunctionsOfProg(trans.getImProg()));
        int totalNodes = 0;
        List<Pair<ImFunction, Integer>> bySize = new ArrayList<>(functions.size());
        for (ImFunction f : functions) {
            int size = estimateSize(f.getBody());
            totalNodes += size;
            bySize.add(Pair.create(f, size));
        }
        bySize.sort((a, b) -> Integer.compare(b.getB(), a.getB()));
        StringBuilder top = new StringBuilder();
        int limit = Math.min(5, bySize.size());
        for (int i = 0; i < limit; i++) {
            Pair<ImFunction, Integer> p = bySize.get(i);
            if (i > 0) {
                top.append(", ");
            }
            top.append(p.getA().getName()).append("=").append(p.getB());
        }
        logLocalOpt(label + " functions=" + functions.size() + " totalNodes=" + totalNodes + " top=[" + top + "]");
    }

    private int estimateSize(Element e) {
        int size = 0;
        for (int i = 0; i < e.size(); i++) {
            size += 1 + estimateSize(e.get(i));
        }
        return size;
    }

}
