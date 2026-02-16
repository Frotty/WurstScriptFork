package de.peeeq.wurstscript.translation.imoptimizer;

import com.google.common.collect.Lists;
import de.peeeq.wurstio.TimeTaker;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.intermediatelang.optimizer.*;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.types.TypesHelper;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.validation.TRVEHelper;

import java.util.*;

import static de.peeeq.wurstscript.validation.TRVEHelper.TO_KEEP;

public class ImOptimizer {
    private int totalFunctionsRemoved = 0;
    private int totalGlobalsRemoved = 0;

    public static int localOptRounds = 1;
    private static final ArrayList<OptimizerPass> localPasses = new ArrayList<>();
    private static final HashMap<String, Integer> totalCount = new HashMap<>();

    static {
        localPasses.add(new SimpleRewrites());
        localPasses.add(new LocalMerger());
        localPasses.add(new BranchMerger());
        localPasses.add(new ConstantAndCopyPropagation());
        localPasses.add(new UselessFunctionCallsRemover());
        localPasses.add(new GlobalsInliner());
        localPasses.add(new SimpleRewrites());
//        localPasses.add(new LocalInliner());
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

        removeGarbage();
        trans.getImProg().flatten(trans);

        int finalItr = 0;
        for (int i = 1; i <= localOptRounds && optCount > 0; i++) {
            optCount = 0;
            StringBuilder sb = new StringBuilder();
            for (OptimizerPass pass : localPasses) {
                int count = timeTaker.measure(pass.getName(), () -> pass.optimize(trans));
                sb.append("<").append(pass.getName()).append(": ").append(count).append("> ");
                optCount += count;
                totalCount.put(pass.getName(), totalCount.getOrDefault(pass.getName(), 0) + count);
            }

            WLogger.info(sb.toString());
            removeGarbage();
            trans.getImProg().flatten(trans);

            finalItr = i;
            WLogger.info("=== Optimization pass: " + i + " opts: " + optCount + " ===");

            // Run a strict inliner to get rid of one-liners
            doStrictInline();
        }
        WLogger.info("=== Local optimizations done! Ran " + finalItr + " passes. ===");
        StringBuilder sb = new StringBuilder("Total: ");
        totalCount.forEach((k, v) -> sb.append("<").append(k).append(": ").append(v).append("> "));
        WLogger.info(sb.toString());

        InitFunctionCleaner.clean(trans.getImProg());
    }

    public void doNullsetting() {
        NullSetter ns = new NullSetter(trans);
        ns.optimize();
        trans.assertProperties();
    }

    public void removeGarbage() {
        boolean changes = true;
        int iterations = 0;
        while (changes && iterations++ < 10) {
            ImProg prog = trans.imProg();
            trans.calculateCallRelationsAndUsedVariables();

            // keep only used variables
            int globalsBefore = prog.getGlobals().size();

            int globalsCount = prog.getGlobals().size();
            Set<ImVar> readVars = trans.getReadVariables();
            prog.getGlobals().removeIf(g ->
                !readVars.contains(g) && !TO_KEEP.contains(g.getName())
            );
            changes = prog.getGlobals().size() != globalsCount;
            int globalsAfter = prog.getGlobals().size();
            int globalsRemoved = globalsBefore - globalsAfter;
            totalGlobalsRemoved += globalsRemoved;

            // keep only functions reachable from main and config
            int functionsBefore = prog.getFunctions().size();
            changes |= prog.getFunctions().retainAll(trans.getUsedFunctions());
            int functionsAfter = prog.getFunctions().size();
            int functionsRemoved = functionsBefore - functionsAfter;
            totalFunctionsRemoved += functionsRemoved;

            // also consider class functions
            Set<ImFunction> allFunctions = new HashSet<>(prog.getFunctions());
            for (ImClass c : prog.getClasses()) {
                int classFunctionsBefore = c.getFunctions().size();
                changes |= c.getFunctions().retainAll(trans.getUsedFunctions());
                int classFunctionsAfter = c.getFunctions().size();
                totalFunctionsRemoved += classFunctionsBefore - classFunctionsAfter;
                allFunctions.addAll(c.getFunctions());

                int classFieldsBefore = c.getFields().size();
                changes |= c.getFields().retainAll(trans.getReadVariables());
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

                            if (!trans.getReadVariables().contains(va.getVar()) && !TO_KEEP.contains(va.getVar().getName())) {
                                replacements.add(Pair.create(e, Collections.singletonList(e.getRight())));
                            }
                        } else if (e.getLeft() instanceof ImVarArrayAccess) {
                            ImVarArrayAccess va = (ImVarArrayAccess) e.getLeft();

                            if (!trans.getReadVariables().contains(va.getVar()) && !TO_KEEP.contains(va.getVar().getName())) {
                                List<ImExpr> exprs = new ArrayList<>();
                                va.getIndexes().forEach(idx -> {
                                    idx.setParent(null);
                                    exprs.add(idx);
                                });
                                va.getIndexes().clear();
                                exprs.add(e.getRight());
                                replacements.add(Pair.create(e, exprs));
                            }
                        } else if (e.getLeft() instanceof ImTupleSelection) {
                            ImTupleSelection ts = (ImTupleSelection) e.getLeft();
                            ImVar var = TypesHelper.getTupleVar(ts);
                            if (!trans.getReadVariables().contains(var) && !TO_KEEP.contains(var.getName())) {
                                List<ImExpr> exprs = new ArrayList<>();
                                ts.getTupleExpr().setParent(null);
                                exprs.add(ts.getTupleExpr());
                                exprs.add(e.getRight());
                                replacements.add(Pair.create(e, exprs));
                            }
                        } else if(e.getLeft() instanceof ImMemberAccess) {
                            ImMemberAccess ma = ((ImMemberAccess) e.getLeft());
                            if (!trans.getReadVariables().contains(ma.getVar()) && !TO_KEEP.contains(ma.getVar().getName())) {
                                List<ImExpr> exprs = new ArrayList<>();
                                ma.getReceiver().setParent(null);
                                exprs.add(ma.getReceiver());
                                ma.getIndexes().forEach(idx -> {
                                    idx.setParent(null);
                                    exprs.add(idx);
                                });
                                ma.getIndexes().clear();
                                exprs.add(e.getRight());
                                replacements.add(Pair.create(e, exprs));
                            }
                        }
                    }
                });

                Replacer replacer = new Replacer();
                for (Pair<ImStmt, List<ImExpr>> pair : replacements) {
                    changes = true;
                    ImExpr r;
                    if (pair.getB().size() == 1) {
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
                changes |= f.getLocals().retainAll(trans.getReadVariables());
            }
        }
    }


    public void doStrictInline() {
        WLogger.info("execute strict inline");
        ImInliner inliner = new ImInliner(trans);
        inliner.setInlineTreshold(1);
        inliner.doInlining();
        trans.assertProperties();
        removeGarbage();
        trans.getImProg().flatten(trans);
    }

    public void encryptStrings() {
        WLogger.info("encrypting strings");
        StringCryptor.encrypt(trans);
        removeGarbage();
        trans.getImProg().flatten(trans);
    }

}
