package de.peeeq.wurstscript.translation.imoptimizer;

import com.google.common.collect.Lists;
import de.peeeq.wurstio.TimeTaker;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.intermediatelang.optimizer.*;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.utils.Pair;
import de.peeeq.wurstscript.validation.TRVEHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class ImOptimizer {
    private int totalFunctionsRemoved = 0;
    private int totalGlobalsRemoved = 0;

    private static final ArrayList<OptimizerPass> localPasses = new ArrayList<>();
    private static final HashMap<String, Integer> totalCount = new HashMap<>();

    static {
        localPasses.add(new ConstantAndCopyPropagation());
        localPasses.add(new UselessFunctionCallsRemover());
        localPasses.add(new GlobalsInliner());
        localPasses.add(new BranchMerger());
        localPasses.add(new SimpleRewrites());
        localPasses.add(new TempMerger());
        localPasses.add(new LocalMerger());
        localPasses.add(new LocalInliner());
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
        ImProg imProg = trans.imProg();

        totalCount.clear();
        removeGarbage();

        int finalItr = 0;
        for (int i = 1; i <= 10 && optCount > 0; i++) {
            optCount = 0;
            localPasses.forEach(pass -> {
                WLogger.info("executing localopt " + pass.getName());
                int count = timeTaker.measure(pass.getName(), () -> pass.optimize(trans));
                optCount += count;
                totalCount.put(pass.getName(), totalCount.getOrDefault(pass.getName(), 0) + count);
            });
            trans.getImProg().flatten(trans);
            removeGarbage();

            finalItr = i;
            WLogger.info("=== Optimization pass: " + i + " opts: " + optCount + " ===");

            // Run a strict inliner to get rid of one-liners
            doStrictInline();
        }
        WLogger.info("=== Local optimizations done! Ran " + finalItr + " passes. ===");
        totalCount.forEach((k, v) -> {
            WLogger.info("== " + k + ":   " + v);
        });
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
            changes = prog.getGlobals().retainAll(trans.getReadVariables());
            int globalsAfter = prog.getGlobals().size();
            int globalsRemoved = globalsBefore - globalsAfter;
            totalGlobalsRemoved += globalsRemoved;
            // keep only functions reachable from main and config
            int functionsBefore = prog.getFunctions().size();
            changes |= prog.getFunctions().retainAll(trans.getUsedFunctions());
            int functionsAfter = prog.getFunctions().size();
            int functionsRemoved = functionsBefore - functionsAfter;
            totalFunctionsRemoved += functionsRemoved;
            for (ImFunction f : prog.getFunctions()) {
                // remove set statements to unread variables
                final List<Pair<ImStmt, List<ImExpr>>> replacements = Lists.newArrayList();
                f.accept(new ImFunction.DefaultVisitor() {
                    @Override
                    public void visit(ImSet e) {
                        super.visit(e);
                        if (e.getLeft() instanceof ImVarAccess) {
                            ImVarAccess va = (ImVarAccess) e.getLeft();
                            WLogger.info("remove gb getVar name: " + va.getVar().getName());
                            if (!trans.getReadVariables().contains(va.getVar()) && !TRVEHelper.TO_KEEP.contains(va.getVar().getName())) {
                                replacements.add(Pair.create(e, Collections.singletonList(e.getRight())));
                            }
                        } else if (e.getLeft() instanceof ImVarArrayAccess) {
                            ImVarArrayAccess va = (ImVarArrayAccess) e.getLeft();
                            WLogger.info("remove gb getVar name: " + va.getVar().getName());
                            if (!trans.getReadVariables().contains(va.getVar()) && !TRVEHelper.TO_KEEP.contains(va.getVar().getName())) {
                                // TODO indexes might have side effects that we need to keep
                                List<ImExpr> exprs = va.getIndexes().removeAll();
                                exprs.add(e.getRight());
                                replacements.add(Pair.create(e, exprs));
                            }
                        }
                    }

                });
                for (Pair<ImStmt, List<ImExpr>> pair : replacements) {
                    changes = true;
                    ImExpr r;
                    if (pair.getB().size() == 1) {
                        r = pair.getB().get(0);
                        r.setParent(null);
                    } else {
                        List<ImStmt> exprs = Collections.unmodifiableList(pair.getB());
                        for (ImStmt expr : exprs) {
                            expr.setParent(null);
                        }
                        r = ImHelper.statementExprVoid(JassIm.ImStmts(exprs));
                    }
                    pair.getA().replaceBy(r);
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
}
