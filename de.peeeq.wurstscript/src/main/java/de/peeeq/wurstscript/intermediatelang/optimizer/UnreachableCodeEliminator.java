package de.peeeq.wurstscript.intermediatelang.optimizer;

import de.peeeq.wurstscript.intermediatelang.optimizer.ControlFlowGraph.Node;
import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.jassIm.ImProg;
import de.peeeq.wurstscript.jassIm.ImStmt;
import de.peeeq.wurstscript.jassIm.ImStmts;
import de.peeeq.wurstscript.translation.imoptimizer.OptimizerPass;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Eliminates unreachable statements using CFG reachability.
 */
public class UnreachableCodeEliminator implements OptimizerPass {
    private int totalRemoved = 0;

    @Override
    public int optimize(ImTranslator trans) {
        ImProg prog = trans.getImProg();
        totalRemoved = 0;

        for (ImFunction f : ImHelper.calculateFunctionsOfProg(prog)) {
            if (!f.isNative() && !f.isBj()) {
                totalRemoved += eliminateInFunction(f);
            }
        }

        return totalRemoved;
    }

    @Override
    public String getName() {
        return "Unreachable code eliminated";
    }

    private int eliminateInFunction(ImFunction func) {
        ControlFlowGraph cfg = new ControlFlowGraph(func.getBody());
        List<Node> nodes = cfg.getNodes();
        if (nodes.isEmpty()) {
            return 0;
        }

        Set<Node> reachable = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<Node> work = new ArrayDeque<>();
        Node entry = nodes.get(0);
        reachable.add(entry);
        work.add(entry);

        while (!work.isEmpty()) {
            Node n = work.pollFirst();
            for (Node succ : n.getSuccessors()) {
                if (reachable.add(succ)) {
                    work.addLast(succ);
                }
            }
        }

        Set<ImStmt> toRemove = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Node n : nodes) {
            if (!reachable.contains(n)) {
                ImStmt s = n.getOwnerStmt();
                if (s != null && s.getParent() instanceof ImStmts) {
                    toRemove.add(s);
                }
            }
        }

        int removed = 0;
        for (ImStmt s : toRemove) {
            if (s.getParent() instanceof ImStmts) {
                ((ImStmts) s.getParent()).remove(s);
                removed++;
            }
        }
        return removed;
    }
}
