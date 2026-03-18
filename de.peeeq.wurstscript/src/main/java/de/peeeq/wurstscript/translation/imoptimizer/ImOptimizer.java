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

    public static int localOptRounds = 10;
    private static final ArrayList<OptimizerPass> localPasses = new ArrayList<>();
    private static final ArrayList<OptimizerPass> preInlinePasses = new ArrayList<>();
    private static final HashMap<String, Integer> totalCount = new HashMap<>();
    public static boolean RUN_LOCALMERGER_AFTER_INLINING = true;
    public static boolean RUN_PREINLINE_LOCAL_OPTS = true;
    public static long LOCAL_PASS_HEARTBEAT_MS = 0L;
    public static int STRICT_INLINE_MAX_SIZE = 8;
    public static boolean STRICT_INLINE_PROFILE = false;
    public static boolean ALLOW_MULTI_RETURN_INLINING = true;

    private static void logLocalOpt(String msg) {
        // Keep detailed pass-level logging disabled by default.
    }

    private static void logLocalOptAlways(String msg) {
        WLogger.warning("[LocalOpt] " + msg);
    }

    static {
        preInlinePasses.add(new SimpleRewrites());
        preInlinePasses.add(new ConstantAndCopyPropagation());
        preInlinePasses.add(new BranchMerger());

        localPasses.add(new SimpleRewrites());
        localPasses.add(new LocalMerger()); // conditionally skipped at runtime via RUN_LOCALMERGER_AFTER_INLINING
        localPasses.add(new BranchMerger());
        localPasses.add(new ConstantAndCopyPropagation());
        localPasses.add(new UselessFunctionCallsRemover());
        localPasses.add(new GlobalsInliner());
        localPasses.add(new DispatchCheckDeduplicator());
        localPasses.add(new SimpleRewrites());
    }

    private final TimeTaker timeTaker;
    ImTranslator trans;
    private boolean localOptsAfterRegularInlining = false;

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
        localOptsAfterRegularInlining = true;
        // remove garbage to reduce work for the inliner
        removeGarbage();
        trans.getImProg().flatten(trans);
        if (RUN_PREINLINE_LOCAL_OPTS) {
            for (OptimizerPass pass : preInlinePasses) {
                timeTaker.measure(pass.getName() + " (pre-inline)", () -> pass.optimize(trans));
            }
            trans.getImProg().flatten(trans);
            removeGarbage();
        }
        // Run LocalMerger before inlining while functions are still smaller.
        timeTaker.measure("Local variables merged (pre-inline)", () -> new LocalMerger().optimize(trans));
        trans.getImProg().flatten(trans);
        removeGarbage();
        GlobalsInliner globalsInliner = new GlobalsInliner();
        globalsInliner.optimize(trans);
        ImInliner inliner = new ImInliner(trans);
        inliner.setAllowMultiReturnInlining(ALLOW_MULTI_RETURN_INLINING);
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
        Set<ImFunction> activeLocalFunctions = collectLocalFunctions();
        for (int i = 1; i <= localOptRounds && optCount > 0; i++) {
            optCount = 0;
            StringBuilder sb = new StringBuilder();
            logLocalOpt("round " + i + " start");
            Set<ImFunction> strictInlineChangedFunctions = Collections.newSetFromMap(new IdentityHashMap<>());
            boolean skipStrictInlineThisRound = i == 1 && localOptsAfterRegularInlining && localOptRounds > 1;
            if (!skipStrictInlineThisRound) {
                logLocalOpt("round " + i + ": strict inline start");
                strictInlineChangedFunctions = doStrictInlineAndCollectChangedFunctions();
                int strictInlineChangedCount = strictInlineChangedFunctions.size();
                optCount += strictInlineChangedCount;
                logLocalOpt("round " + i + ": strict inline done, changedFuncs=" + strictInlineChangedCount);
                if (!strictInlineChangedFunctions.isEmpty()) {
                    activeLocalFunctions.addAll(strictInlineChangedFunctions);
                }
            } else {
                logLocalOpt("round " + i + ": strict inline skipped (first post-inlining round)");
            }
            Set<ImFunction> roundChangedLocalFunctions = Collections.newSetFromMap(new IdentityHashMap<>());
            roundChangedLocalFunctions.addAll(strictInlineChangedFunctions);
            boolean nonLocalPassChanged = false;
            for (OptimizerPass pass : localPasses) {
                PassRunResult result = runLocalPassWithHeartbeat(pass, i, activeLocalFunctions);
                int count = result.count;
                if (count != 0) {
                    sb.append("<").append(pass.getName()).append(": ").append(count).append("> ");
                }
                optCount += count;
                totalCount.put(pass.getName(), totalCount.getOrDefault(pass.getName(), 0) + count);
                if (result.allLocalFunctionsMayHaveChanged) {
                    nonLocalPassChanged = true;
                } else {
                    roundChangedLocalFunctions.addAll(result.changedFunctions);
                }
            }

            if (optCount > 0) {
                logLocalOpt("round " + i + ": cleanup after pass changes");
                removeGarbage();
                trans.getImProg().flatten(trans);
                if (nonLocalPassChanged) {
                    activeLocalFunctions = collectLocalFunctions();
                } else {
                    roundChangedLocalFunctions.removeIf(f -> f.getParent() == null);
                    activeLocalFunctions = roundChangedLocalFunctions;
                }
            }

            finalItr = i;
            String roundDetails = sb.length() == 0 ? "<no-pass-delta>" : sb.toString().trim();
            logLocalOptAlways("round " + i + " done, opts=" + optCount + " " + roundDetails);
        }
        logLocalOptAlways("done, rounds=" + finalItr);
        StringBuilder sb = new StringBuilder("Total: ");
        totalCount.forEach((k, v) -> {
            if (v != 0) {
                sb.append("<").append(k).append(": ").append(v).append("> ");
            }
        });
        if (sb.toString().equals("Total: ")) {
            sb.append("<no-pass-delta>");
        }
        logLocalOptAlways(sb.toString());

        InitFunctionCleaner.clean(trans.getImProg());
        localOptsAfterRegularInlining = false;
    }

    private PassRunResult runLocalPassWithHeartbeat(OptimizerPass pass, int round, Set<ImFunction> activeLocalFunctions) {
        final String passName = pass.getName();
        final long started = System.nanoTime();
        final boolean heartbeatEnabled = LOCAL_PASS_HEARTBEAT_MS > 0;
        final AtomicBoolean running = new AtomicBoolean(heartbeatEnabled);
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
        if (heartbeatEnabled) {
            heartbeat.start();
        }
        try {
            PassRunResult result = timeTaker.measure(passName, () -> runLocalPass(pass, activeLocalFunctions));
            if (result.count > 0) {
                // Mutating passes can invalidate cached read/write attributes.
                trans.getImProg().clearAttributes();
            }
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            if (pass instanceof LocalOptimizerPass) {
                logLocalOpt("round=" + round + " END pass='" + passName + "' count=" + result.count
                    + " touched=" + result.touchedFunctions + " changedFuncs=" + result.changedFunctions.size()
                    + " elapsedMs=" + elapsedMs);
            } else {
                logLocalOpt("round=" + round + " END pass='" + passName + "' count=" + result.count + " elapsedMs=" + elapsedMs);
            }
            return result;
        } catch (Throwable t) {
            logLocalOptAlways("round=" + round + " FAIL pass='" + passName + "' after " + ((System.nanoTime() - started) / 1_000_000L) + " ms: " + t);
            throw t;
        } finally {
            if (heartbeatEnabled) {
                running.set(false);
                heartbeat.interrupt();
            }
        }
    }

    private PassRunResult runLocalPass(OptimizerPass pass, Set<ImFunction> activeLocalFunctions) {
        if (pass instanceof LocalMerger && !RUN_LOCALMERGER_AFTER_INLINING) {
            return new PassRunResult(0, Collections.emptySet(), 0, false);
        }
        if (!(pass instanceof LocalOptimizerPass)) {
            int count = pass.optimize(trans);
            return new PassRunResult(count, Collections.emptySet(), 0, count > 0);
        }
        LocalOptimizerPass localPass = (LocalOptimizerPass) pass;
        Set<ImFunction> changedFunctions = Collections.newSetFromMap(new IdentityHashMap<>());
        int count = 0;
        int touched = 0;
        localPass.beginRound(trans);
        try {
            for (ImFunction f : activeLocalFunctions) {
                if (!localPass.shouldOptimize(f)) {
                    continue;
                }
                touched++;
                long before = functionFingerprint(f);
                count += localPass.optimizeFunction(f, trans);
                long after = functionFingerprint(f);
                if (before != after) {
                    changedFunctions.add(f);
                }
            }
        } finally {
            localPass.endRound(trans);
        }
        return new PassRunResult(count, changedFunctions, touched, false);
    }

    private Set<ImFunction> collectLocalFunctions() {
        Set<ImFunction> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ImFunction func : ImHelper.calculateFunctionsOfProg(trans.getImProg())) {
            if (!func.isNative() && !func.isBj()) {
                result.add(func);
            }
        }
        return result;
    }

    private static final class PassRunResult {
        final int count;
        final Set<ImFunction> changedFunctions;
        final int touchedFunctions;
        final boolean allLocalFunctionsMayHaveChanged;

        private PassRunResult(int count, Set<ImFunction> changedFunctions, int touchedFunctions, boolean allLocalFunctionsMayHaveChanged) {
            this.count = count;
            this.changedFunctions = changedFunctions;
            this.touchedFunctions = touchedFunctions;
            this.allLocalFunctionsMayHaveChanged = allLocalFunctionsMayHaveChanged;
        }
    }

    private static long functionFingerprint(ImFunction func) {
        long h = 0xcbf29ce484222325L;
        h = mix(h, func.getName().hashCode());
        h = mix(h, func.getParameters().size());
        h = mix(h, func.getLocals().size());
        for (ImVar p : func.getParameters()) {
            h = mix(h, p.getName().hashCode());
            h = mix(h, p.getType().hashCode());
        }
        for (ImVar l : func.getLocals()) {
            h = mix(h, l.getName().hashCode());
            h = mix(h, l.getType().hashCode());
        }
        ArrayDeque<Element> stack = new ArrayDeque<>();
        stack.push(func.getBody());
        while (!stack.isEmpty()) {
            Element e = stack.pop();
            h = mix(h, e.getClass().hashCode());
            h = mix(h, e.size());
            if (e instanceof ImVarAccess) {
                h = mix(h, System.identityHashCode(((ImVarAccess) e).getVar()));
            } else if (e instanceof ImVarArrayAccess) {
                h = mix(h, System.identityHashCode(((ImVarArrayAccess) e).getVar()));
            } else if (e instanceof ImMemberAccess) {
                h = mix(h, System.identityHashCode(((ImMemberAccess) e).getVar()));
            } else if (e instanceof ImFunctionCall) {
                h = mix(h, System.identityHashCode(((ImFunctionCall) e).getFunc()));
            } else if (e instanceof ImMethodCall) {
                h = mix(h, System.identityHashCode(((ImMethodCall) e).getMethod()));
            } else if (e instanceof ImIntVal) {
                h = mix(h, ((ImIntVal) e).getValI());
            } else if (e instanceof ImBoolVal) {
                h = mix(h, ((ImBoolVal) e).getValB() ? 1 : 0);
            } else if (e instanceof ImRealVal) {
                h = mix(h, ((ImRealVal) e).getValR().hashCode());
            } else if (e instanceof ImStringVal) {
                h = mix(h, ((ImStringVal) e).getValS().hashCode());
            }
            for (int i = e.size() - 1; i >= 0; i--) {
                stack.push(e.get(i));
            }
        }
        return h;
    }

    private static long mix(long h, int v) {
        h ^= (v & 0xffffffffL);
        h *= 0x100000001b3L;
        return h;
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

            // Use removeIf so that TO_KEEP globals are never discarded even when not in readVars.
            // The old retainAll(readVars) did not check TO_KEEP and could incorrectly remove
            // externally-visible globals (e.g. game-engine-read vars set by the user script).
            changes = prog.getGlobals().removeIf(g ->
                !readVars.contains(g) && !TO_KEEP.contains(g.getName())
            );
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

    private Set<ImFunction> doStrictInlineAndCollectChangedFunctions() {
        Set<ImFunction> beforeFunctions = collectLocalFunctions();
        Map<ImFunction, Long> beforeFingerprints = new IdentityHashMap<>();
        for (ImFunction f : beforeFunctions) {
            beforeFingerprints.put(f, functionFingerprint(f));
        }

        doStrictInline();

        Set<ImFunction> afterFunctions = collectLocalFunctions();
        Set<ImFunction> changed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ImFunction f : afterFunctions) {
            Long before = beforeFingerprints.get(f);
            if (before == null) {
                changed.add(f);
                continue;
            }
            long after = functionFingerprint(f);
            if (after != before) {
                changed.add(f);
            }
        }
        return changed;
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
