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
                ImVarWrite write = writes.iterator().next();
                ImVarRead read = reads.iterator().next();
                if (!writeDominatesRead(write, read)) {
                    continue;
                }

                ImExpr writeRight = write.getRight();
                if (!sideEffectAnalyzer.hasSideEffects(writeRight) && sideEffectAnalyzer.usedVariables(writeRight).isEmpty()) {
                    writeRight.setParent(null);
                    read.replaceBy(writeRight);
                    totalLocalsInlined++;
                }
            }
        }
    }


    private boolean writeDominatesRead(ImVarWrite write, ImVarRead read) {
        ImStmt writeStmt = findEnclosingStmt(write);
        ImStmt readStmt = findEnclosingStmt(read);
        if (writeStmt == null || readStmt == null) {
            return false;
        }
        if (writeStmt == readStmt) {
            return false;
        }
        if (!(writeStmt.getParent() instanceof ImStmts)) {
            return false;
        }
        if (writeStmt.getParent() != readStmt.getParent()) {
            return false;
        }
        ImStmts block = (ImStmts) writeStmt.getParent();
        return block.indexOf(writeStmt) < block.indexOf(readStmt);
    }

    private ImStmt findEnclosingStmt(Element element) {
        Element current = element;
        while (current != null && !(current instanceof ImStmt)) {
            current = current.getParent();
        }
        return (ImStmt) current;
    }


}
