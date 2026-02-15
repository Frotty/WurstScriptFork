package de.peeeq.wurstscript.intermediatelang.optimizer;

import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imoptimizer.OptimizerPass;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;

import java.util.ArrayDeque;
import java.util.ListIterator;

/**
 * merges identical nodes in branches if possible without side effects
 * <p>
 * the input must be a flattened program
 */
public class BranchMerger  implements OptimizerPass {
    private SideEffectAnalyzer sideEffectAnalyzer;
    public int branchesMerged = 0;

    @Override
    public int optimize(ImTranslator trans) {
        branchesMerged = 0;
        ImProg prog = trans.getImProg();
        this.sideEffectAnalyzer = new SideEffectAnalyzer(prog);

        for (ImFunction func : prog.getFunctions()) {
            optimizeFunc(func);
        }
        return branchesMerged;
    }

    private void optimizeFunc(ImFunction func) {
        mergeBranches(func);
    }


    private void mergeBranches(ImFunction func) {
        func.getBody().accept(new Element.DefaultVisitor() {
            @Override
            public void visit(ImStmts stmts) {
                ListIterator<ImStmt> it = stmts.listIterator();
                while (it.hasNext()) {
                    ImStmt s = it.next();
                    if (s instanceof ImIf) {
                        ImIf ifStmt = (ImIf) s;
                        // first optimize inner statements
                        ifStmt.getThenBlock().accept(this);
                        ifStmt.getElseBlock().accept(this);

                        while (!ifStmt.getThenBlock().isEmpty()
                                && !ifStmt.getElseBlock().isEmpty()) {
                            ImStmt firstStmtThen = ifStmt.getThenBlock().get(0);
                            ImStmt firstStmtElse = ifStmt.getElseBlock().get(0);
                            // if first statement in both branches is the same
                            // and has no side-effects that could affect the if-condition:
                            if (firstStmtThen.structuralEquals(firstStmtElse)
                                    && !sideEffectAnalyzer.mightAffect(firstStmtThen, ifStmt.getCondition())) {
                                // remove statements
                                ifStmt.getThenBlock().remove(0);
                                ifStmt.getElseBlock().remove(0);
                                // and add before the if-statement
                                it.previous();
                                it.add(firstStmtThen);
                                it.next();

                                branchesMerged++;
                            } else {
                                break;
                            }
                        }

                        ArrayDeque<ImStmt> mergedTail = new ArrayDeque<>();
                        while (!ifStmt.getThenBlock().isEmpty()
                                && !ifStmt.getElseBlock().isEmpty()) {
                            int thenLast = ifStmt.getThenBlock().size() - 1;
                            int elseLast = ifStmt.getElseBlock().size() - 1;
                            ImStmt lastStmtThen = ifStmt.getThenBlock().get(thenLast);
                            ImStmt lastStmtElse = ifStmt.getElseBlock().get(elseLast);
                            if (lastStmtThen.structuralEquals(lastStmtElse)) {
                                ifStmt.getThenBlock().remove(thenLast);
                                ifStmt.getElseBlock().remove(elseLast);
                                mergedTail.addFirst(lastStmtThen);
                                branchesMerged++;
                            } else {
                                break;
                            }
                        }
                        while (!mergedTail.isEmpty()) {
                            it.add(mergedTail.removeFirst());
                        }

                    } else {
                        s.accept(this);
                    }
                }
            }
        });
    }



    @Override
    public String getName() {
        return "Branches merged";
    }
}
