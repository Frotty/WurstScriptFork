package de.peeeq.wurstscript.intermediatelang.optimizer;

import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imoptimizer.OptimizerPass;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;

import java.util.Collection;

/**
 * inlines a local if it is only assigned and read once, without side effects
 */
public class LocalInliner implements OptimizerPass {
    private SideEffectAnalyzer sideEffectAnalyzer;
    private int totalLocalsInlined = 0;
    private ImProg imProg;

    @Override
    public int optimize(ImTranslator trans) {
        imProg = trans.getImProg();
        this.sideEffectAnalyzer = new SideEffectAnalyzer(imProg);
        totalLocalsInlined = 0;
        for (ImFunction func : imProg.getFunctions()) {
            if (!func.isNative() && !func.isBj()) {
                optimizeFunc(func);
            }
        }
        imProg.flatten(trans);
        return totalLocalsInlined;
    }


    @Override
    public String getName() {
        return "Local variables inlined";
    }

    private void optimizeFunc(ImFunction func) {
        ImVars locals = func.getLocals();
        for (ImVar local : locals) {
            Collection<ImVarWrite> writes = local.attrWrites();
            Collection<ImVarRead> reads = local.attrReads();
            if (writes.size() == 1 && reads.size() == 1) {
                ImExpr writeRight = writes.iterator().next().getRight();
                Element nearestStmt = reads.iterator().next().getParent();
                while (nearestStmt != null) {
                    if (nearestStmt instanceof ImStmt) {
                        break;
                    }
                    nearestStmt = nearestStmt.getParent();
                }
                if (!sideEffectAnalyzer.hasSideEffects(writeRight) && sideEffectAnalyzer.usedVariables(writeRight).size() == 0) {
                    writeRight.setParent(null);
                    reads.iterator().next().replaceBy(writeRight);
                    totalLocalsInlined++;
                }
            }
        }
    }


}
