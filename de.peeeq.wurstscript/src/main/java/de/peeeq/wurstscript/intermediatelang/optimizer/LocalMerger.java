package de.peeeq.wurstscript.intermediatelang.optimizer;

import de.peeeq.datastructures.GraphInterpreter;
import de.peeeq.wurstscript.intermediatelang.optimizer.ControlFlowGraph.Node;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imoptimizer.LocalOptimizerPass;
import de.peeeq.wurstscript.translation.imoptimizer.OptimizerPass;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.types.TypesHelper;
import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.*;

public class LocalMerger implements OptimizerPass, LocalOptimizerPass {
    private int totalLocalsMerged = 0;
    private static final boolean PROFILE = Boolean.parseBoolean(System.getProperty("wurst.localmerger.profile", "true"));
    private static final long SLOW_FUNC_MS = Long.getLong("wurst.localmerger.slowFuncMs", 1000L);
    private static final int MAX_SCC_ITER = Integer.getInteger("wurst.localmerger.maxSccIter", 1_000_000);
    /** Set to true to use upstream interference-graph coloring instead of BitSet interval coloring. */
    public static boolean USE_INTERFERENCE_GRAPH = false;

    @Override
    public int optimize(ImTranslator trans) {
        ImProg prog = trans.getImProg();
        totalLocalsMerged = 0;
        for (ImFunction func : de.peeeq.wurstscript.translation.imtranslation.ImHelper.calculateFunctionsOfProg(prog)) {
            if (!func.isNative() && !func.isBj()) {
                optimizeFunc(func);
            }
        }
        return totalLocalsMerged;
    }

    @Override
    public String getName() { return "Local variables merged"; }

    @Override
    public int optimizeFunction(ImFunction func, ImTranslator trans) {
        int before = totalLocalsMerged;
        optimizeFunc(func);
        return totalLocalsMerged - before;
    }

    void optimizeFunc(ImFunction func) {
        long t0 = System.nanoTime();
        LivenessResult livenessResult = calculateLivenessDetailed(func);
        Map<ImStmt, Set<ImVar>> livenessInfo = livenessResult.livenessInfo;
        eliminateDeadCode(livenessInfo);
        mergeLocals(livenessInfo, func);
        if (PROFILE) {
            long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
            if (elapsedMs >= SLOW_FUNC_MS) {
                de.peeeq.wurstscript.WLogger.warning("[LocalMerger] slow function '" + func.getName()
                    + "' elapsedMs=" + elapsedMs
                    + " nodes=" + livenessResult.nodeCount
                    + " sccCount=" + livenessResult.sccCount
                    + " maxSccSize=" + livenessResult.maxSccSize
                    + " sccIterations=" + livenessResult.totalSccIterations
                    + " maxSccIterations=" + livenessResult.maxSccIterations
                    + " abortedScc=" + livenessResult.abortedSccIterations);
            }
        }
    }

    private boolean canMerge(ImType a, ImType b) { return a.equalsType(b); }

    private void mergeLocals(Map<ImStmt, Set<ImVar>> livenessInfo, ImFunction func) {
        if (USE_INTERFERENCE_GRAPH) {
            mergeLocalsInterferenceGraph(livenessInfo, func);
            return;
        }
        Map<ImVar, BitSet> liveByVar = buildLiveByVar(livenessInfo);
        List<ImVar> params = new ArrayList<>(func.getParameters());
        if (func.hasFlag(de.peeeq.wurstscript.translation.imtranslation.FunctionFlagEnum.IS_VARARG) && !params.isEmpty()) {
            params.remove(params.size() - 1);
        }

        class ColorSlot {
            final ImVar repr;
            final ImType type;
            final BitSet occupied;

            ColorSlot(ImVar repr, BitSet occupied) {
                this.repr = repr;
                this.type = repr.getType();
                this.occupied = occupied;
            }
        }

        List<ColorSlot> slots = new ArrayList<>();
        for (ImVar p : params) {
            BitSet occ = new BitSet();
            BitSet lp = liveByVar.get(p);
            if (lp != null) {
                occ.or(lp);
            }
            slots.add(new ColorSlot(p, occ));
        }

        List<ImVar> locals = new ArrayList<>(func.getLocals());
        locals.sort((a, b) -> Integer.compare(
            liveByVar.getOrDefault(b, new BitSet()).cardinality(),
            liveByVar.getOrDefault(a, new BitSet()).cardinality()
        ));

        Map<ImVar, ImVar> merges = new LinkedHashMap<>();
        for (ImVar v : locals) {
            BitSet live = liveByVar.get(v);
            if (live == null) {
                live = new BitSet();
            }

            ColorSlot target = null;
            for (ColorSlot s : slots) {
                if (!canMerge(s.type, v.getType())) {
                    continue;
                }
                if (!s.occupied.intersects(live)) {
                    target = s;
                    break;
                }
            }
            if (target != null) {
                merges.put(v, target.repr);
                target.occupied.or(live);
            } else {
                slots.add(new ColorSlot(v, (BitSet) live.clone()));
            }
        }

        applyMerges(func, merges);
        int removed = removeUnusedLocals(func);
        totalLocalsMerged += removed;
    }

    /** Upstream interference-graph based coloring (used when USE_INTERFERENCE_GRAPH=true). */
    private void mergeLocalsInterferenceGraph(Map<ImStmt, Set<ImVar>> livenessInfo, ImFunction func) {
        Map<ImVar, Set<ImVar>> interference = calculateInterferenceGraph(livenessInfo);

        PriorityQueue<ImVar> queue = new PriorityQueue<>(
            (x, y) -> interference.get(y).size() - interference.get(x).size()
        );
        queue.addAll(interference.keySet());

        List<ImVar> params = new ArrayList<>(func.getParameters());
        if (func.hasFlag(de.peeeq.wurstscript.translation.imtranslation.FunctionFlagEnum.IS_VARARG) && !params.isEmpty()) {
            params.remove(params.size() - 1);
        }
        queue.removeAll(func.getParameters());

        List<ImVar> colors = new ArrayList<>(params);
        Map<ImVar, ImVar> merges = new LinkedHashMap<>();

        while (!queue.isEmpty()) {
            ImVar v = queue.poll();
            boolean merged = false;
            for (ImVar color : colors) {
                if (!canMerge(color.getType(), v.getType())) continue;
                boolean conflict = false;
                for (ImVar neigh : interference.get(v)) {
                    if (merges.getOrDefault(neigh, neigh) == color) { conflict = true; break; }
                }
                if (!conflict) { merges.put(v, color); merged = true; break; }
            }
            if (!merged) colors.add(v);
        }

        applyMerges(func, merges);
        int removed = removeUnusedLocals(func);
        totalLocalsMerged += removed;
    }

    private Map<ImVar, Set<ImVar>> calculateInterferenceGraph(Map<ImStmt, Set<ImVar>> livenessInfo) {
        Map<ImVar, Set<ImVar>> g = new LinkedHashMap<>();
        for (Map.Entry<ImStmt, Set<ImVar>> e : livenessInfo.entrySet()) {
            Set<ImVar> live = e.getValue();
            for (ImVar v1 : live) {
                Set<ImVar> set = g.getOrDefault(v1, HashSet.empty());
                set = set.addAll(live.filter(v2 -> canMerge(v1.getType(), v2.getType())));
                g.put(v1, set);
            }
        }
        return g;
    }

    private static void applyMerges(ImFunction func, Map<ImVar, ImVar> merges) {
        if (merges.isEmpty()) return;

        func.accept(new ImFunction.DefaultVisitor() {
            @Override public void visit(ImVarAccess va) {
                super.visit(va);
                ImVar m = merges.get(va.getVar());
                if (m != null) va.setVar(m);
            }
            @Override public void visit(ImSet set) {
                super.visit(set);
                if (set.getLeft() instanceof ImVarAccess) {
                    ImVar m = merges.get(((ImVarAccess) set.getLeft()).getVar());
                    if (m != null) {
                        ImVarAccess newAccess = JassIm.ImVarAccess(m);
                        set.getLeft().replaceBy(newAccess);
                    }
                }
            }
            @Override public void visit(ImVarargLoop varargLoop) {
                super.visit(varargLoop);
                ImVar m = merges.get(varargLoop.getLoopVar());
                if (m != null) varargLoop.setLoopVar(m);
            }
        });
    }

    private static int removeUnusedLocals(ImFunction f) {
        final java.util.Set<ImVar> used = new java.util.HashSet<>();
        used.addAll(f.getParameters());
        f.getBody().accept(new Element.DefaultVisitor() {
            @Override public void visit(ImVarAccess va) { super.visit(va); used.add(va.getVar()); }
            @Override public void visit(ImMemberAccess ma) { super.visit(ma); used.add(ma.getVar()); }
            @Override public void visit(ImVarArrayAccess vaa) { super.visit(vaa); used.add(vaa.getVar()); }
        });
        List<ImVar> locals = new ArrayList<>(f.getLocals());
        int before = locals.size();
        List<ImVar> kept = new ArrayList<>(locals.size());
        for (ImVar v : locals) if (used.contains(v)) kept.add(v);
        if (kept.size() != locals.size()) { f.getLocals().clear(); f.getLocals().addAll(kept); }
        return before - kept.size();
    }

    private Map<ImVar, BitSet> buildLiveByVar(Map<ImStmt, Set<ImVar>> livenessInfo) {
        Map<ImVar, BitSet> liveByVar = new LinkedHashMap<>();
        int stmtIdx = 0;
        for (Map.Entry<ImStmt, Set<ImVar>> e : livenessInfo.entrySet()) {
            for (ImVar v : e.getValue()) {
                liveByVar.computeIfAbsent(v, __ -> new BitSet()).set(stmtIdx);
            }
            stmtIdx++;
        }
        return liveByVar;
    }

    private void eliminateDeadCode(Map<ImStmt, Set<ImVar>> livenessInfo) {
        for (ImStmt s : livenessInfo.keySet()) {
            if (!(s instanceof ImSet)) continue;

            ImSet set = (ImSet) s;
            ImLExpr lhs = set.getLeft();

            if (lhs instanceof ImVarAccess && set.getRight() instanceof ImVarAccess) {
                if (((ImVarAccess) lhs).getVar() == ((ImVarAccess) set.getRight()).getVar()) {
                    s.replaceBy(ImHelper.nullExpr());
                    continue;
                }
            }

            ImVar v = null;
            if (lhs instanceof ImVarAccess) {
                v = ((ImVarAccess) lhs).getVar();
            } else if (lhs instanceof ImTupleSelection) {
                v = TypesHelper.getSimpleAndPureTupleVar((ImTupleSelection) lhs);
            }

            if (!isTrackableLocal(v)) continue;

            if (!livenessInfo.get(s).contains(v)) {
                final List<ImExpr> raw = new ArrayList<>();
                collectLhsSideEffects(lhs, raw);
                if (hasSideEffects(set.getRight())) raw.add(set.getRight());

                if (raw.isEmpty()) {
                    AstEdits.deleteStmt(s);  // remove the dead assignment entirely
                } else {
                    ImStmts block = JassIm.ImStmts();
                    for (ImExpr e : raw) {
                        // wrap expression as a statement; add a *copy* to avoid re-parenting conflicts
                        block.add(ImHelper.statementExprVoid(e.copy()));
                    }
                    AstEdits.replaceStmtWithMany(s, block); // removes 's', then inserts the new stmts
                }
            }
        }
    }

    private static void collectLhsSideEffects(ImLExpr lhs, List<ImExpr> out) {
        if (lhs instanceof ImVarArrayAccess a) {
            for (ImExpr idx : a.getIndexes()) if (hasSideEffects(idx)) out.add(idx);
        } else if (lhs instanceof ImMemberAccess m) {
            if (hasSideEffects(m.getReceiver())) out.add(m.getReceiver());
            for (ImExpr idx : m.getIndexes()) if (hasSideEffects(idx)) out.add(idx);
        } else if (lhs instanceof ImTupleSelection ts) {
            Element t = ts.getTupleExpr();
            if (hasSideEffects(t)) out.add((ImExpr) t);
        }
    }


    private static boolean hasSideEffects(Element e) {
        if (e instanceof ImFunctionCall || e instanceof ImMethodCall) return true;
        if (e instanceof ImAlloc || e instanceof ImDealloc) return true;
        for (int i = 0; i < e.size(); i++) if (hasSideEffects(e.get(i))) return true;
        return false;
    }

    /**
     * Some temporary vars can be referenced before being attached to the IM tree.
     * isGlobal() throws for those, so we skip them in local liveness/merge tracking.
     */
    private static boolean isTrackableLocal(ImVar v) {
        return v != null && isAttachedToProg(v) && !v.isGlobal();
    }

    private static boolean isAttachedToProg(Element e) {
        Element cur = e;
        while (cur != null) {
            if (cur instanceof ImProg) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    /**
     * Calculates liveness for each statement using a fixed-point iteration
     * over the strongly connected components of the control flow graph.
     */
    public Map<ImStmt, Set<ImVar>> calculateLiveness(ImFunction func) {
        return calculateLivenessDetailed(func).livenessInfo;
    }

    private static final class LivenessResult {
        final Map<ImStmt, Set<ImVar>> livenessInfo;
        final int nodeCount;
        final int sccCount;
        final int maxSccSize;
        final long totalSccIterations;
        final long maxSccIterations;
        final boolean abortedSccIterations;

        private LivenessResult(
            Map<ImStmt, Set<ImVar>> livenessInfo,
            int nodeCount,
            int sccCount,
            int maxSccSize,
            long totalSccIterations,
            long maxSccIterations,
            boolean abortedSccIterations
        ) {
            this.livenessInfo = livenessInfo;
            this.nodeCount = nodeCount;
            this.sccCount = sccCount;
            this.maxSccSize = maxSccSize;
            this.totalSccIterations = totalSccIterations;
            this.maxSccIterations = maxSccIterations;
            this.abortedSccIterations = abortedSccIterations;
        }
    }

    private LivenessResult calculateLivenessDetailed(ImFunction func) {
        // 1. Build Control Flow Graph
        ControlFlowGraph cfg = new ControlFlowGraph(func.getBody());
        final List<Node> nodes = cfg.getNodes();
        final int N = nodes.size();

        // Map nodes to indices for quick array access
        final Object2IntOpenHashMap<Node> idx = new Object2IntOpenHashMap<>(N);
        idx.defaultReturnValue(-1);
        for (int i = 0; i < N; i++) idx.put(nodes.get(i), i);

        // 2. Calculate USE and DEF bitsets for each node
        final Object2IntOpenHashMap<ImVar> varToId = new Object2IntOpenHashMap<>();
        varToId.defaultReturnValue(-1);
        final ArrayList<ImVar> idToVar = new ArrayList<>();
        final BitSet[] use = new BitSet[N];
        final BitSet[] def = new BitSet[N];

        for (int i = 0; i < N; i++) {
            Node node = nodes.get(i);
            use[i] = new BitSet();
            def[i] = new BitSet();

            ImStmt stmt = node.getStmt();
            if (stmt == null) continue;

            final int ii = i;
            stmt.accept(new ImStmt.DefaultVisitor() {
                @Override public void visit(ImVarAccess va) {
                    super.visit(va);
                    ImVar v = va.getVar();
                    if (isTrackableLocal(v)) use[ii].set(varId(v, varToId, idToVar));
                }
                @Override public void visit(ImSet set) {
                    set.getRight().accept(this);
                    Element.DefaultVisitor me = this;
                    set.getLeft().match(new ImLExpr.MatcherVoid() {
                        @Override public void case_ImTupleSelection(ImTupleSelection e) { ((ImLExpr) e.getTupleExpr()).match(this); }
                        @Override public void case_ImVarAccess(ImVarAccess e) {}
                        @Override public void case_ImVarArrayAccess(ImVarArrayAccess e) { e.getIndexes().accept(me); }
                        @Override public void case_ImMemberAccess(ImMemberAccess e) { e.getReceiver().accept(me); e.getIndexes().accept(me); }
                        @Override public void case_ImStatementExpr(ImStatementExpr e) { e.getStatements().accept(me); ((ImLExpr) e.getExpr()).match(this); }
                        @Override public void case_ImTupleExpr(ImTupleExpr e) { for (ImExpr ex : e.getExprs()) ((ImLExpr) ex).match(this); }
                    });
                }
            });

            if (stmt instanceof ImSet) {
                ImSet set = (ImSet) stmt;
                if (set.getLeft() instanceof ImVarAccess) {
                    ImVar v = ((ImVarAccess) set.getLeft()).getVar();
                    if (isTrackableLocal(v)) def[i].set(varId(v, varToId, idToVar));
                }
            }
        }
        final int varCount = idToVar.size();

        // 3. Find SCCs on the REVERSED graph for backward analysis
        GraphInterpreter<Node> reverseCfgInterpreter = new GraphInterpreter<>() {
            @Override
            protected Collection<Node> getIncidentNodes(Node t) {
                // For backward analysis, we traverse predecessors
                return t.getPredecessors();
            }
        };
        // Use the path-based strong component algorithm [1] on the reversed CFG.
        // It returns SCCs in reverse topological order of the graph it is given.
        List<List<Node>> sccs = reverseCfgInterpreter.findStronglyConnectedComponents(nodes);
        // For a backward analysis, we need to process SCCs in reverse topological order of the original CFG.
        // The algorithm on the reversed graph gives a topological sort of the original graph's SCCs.
        // Therefore, we reverse the list to get the required processing order.
        Collections.reverse(sccs);
        int maxSccSize = 0;
        for (List<Node> scc : sccs) {
            if (scc.size() > maxSccSize) {
                maxSccSize = scc.size();
            }
        }
        boolean hasCycles = false;
        for (List<Node> scc : sccs) {
            if (scc.size() > 1) {
                hasCycles = true;
                break;
            }
            if (scc.size() == 1) {
                Node n = scc.get(0);
                if (n.getSuccessors().contains(n)) {
                    hasCycles = true;
                    break;
                }
            }
        }

        // 4. Initialize IN and OUT sets for the data-flow analysis
        final BitSet[] in  = new BitSet[N];
        final BitSet[] out = new BitSet[N];
        for (int i = 0; i < N; i++) {
            in[i] = new BitSet(varCount);
            out[i] = new BitSet(varCount);
        }

        // 5. Iterate over SCCs in reverse topological order
        long totalSccIterations = 0;
        long maxSccIterations = 0;
        boolean abortedSccIterations = false;
        final BitSet tmpOut = new BitSet(varCount);
        final BitSet tmpIn = new BitSet(varCount);
        for (List<Node> scc : sccs) {
            if (scc.isEmpty()) continue;

            if (!hasCycles) {
                // Acyclic CFG fast-path: one reverse-topological pass is enough.
                for (Node uNode : scc) {
                    int u = idx.getInt(uNode);
                    computeOutIn(uNode, u, idx, in, out, use, def, tmpOut, tmpIn);
                }
                totalSccIterations += 1;
                if (maxSccIterations < 1) maxSccIterations = 1;
                continue;
            }

            // Cyclic case: SCC-local worklist fixpoint to avoid scanning all nodes every round.
            int sccIterations = 0;
            java.util.Set<Node> sccSet = new java.util.HashSet<>(scc);
            java.util.ArrayDeque<Node> worklist = new java.util.ArrayDeque<>(scc);
            java.util.HashSet<Node> enqueued = new java.util.HashSet<>(scc);
            while (!worklist.isEmpty()) {
                Node uNode = worklist.pollFirst();
                enqueued.remove(uNode);
                sccIterations++;
                if (sccIterations > MAX_SCC_ITER) {
                    abortedSccIterations = true;
                    de.peeeq.wurstscript.WLogger.warning("[LocalMerger] aborting SCC worklist for function '" + func.getName()
                        + "' after iterations=" + sccIterations + " sccSize=" + scc.size() + " nodes=" + N);
                    break;
                }
                int u = idx.getInt(uNode);
                boolean changed = computeOutIn(uNode, u, idx, in, out, use, def, tmpOut, tmpIn);
                if (changed) {
                    for (Node pred : uNode.getPredecessors()) {
                        if (sccSet.contains(pred) && enqueued.add(pred)) {
                            worklist.addLast(pred);
                        }
                    }
                }
            }
            totalSccIterations += sccIterations;
            if (sccIterations > maxSccIterations) {
                maxSccIterations = sccIterations;
            }
        }

        // 6. Collect results into the final map format
        final java.util.LinkedHashMap<ImStmt, Set<ImVar>> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < N; i++) {
            ImStmt stmt = nodes.get(i).getStmt();
            if (stmt != null) {
                ObjectOpenHashSet<ImVar> live = new ObjectOpenHashSet<>();
                for (int bit = out[i].nextSetBit(0); bit >= 0; bit = out[i].nextSetBit(bit + 1)) {
                    live.add(idToVar.get(bit));
                }
                result.put(stmt, HashSet.ofAll(live));
            }
        }
        return new LivenessResult(result, N, sccs.size(), maxSccSize, totalSccIterations, maxSccIterations, abortedSccIterations);
    }

    private static int varId(ImVar v, Object2IntOpenHashMap<ImVar> varToId, ArrayList<ImVar> idToVar) {
        int id = varToId.getInt(v);
        if (id >= 0) {
            return id;
        }
        int newId = idToVar.size();
        varToId.put(v, newId);
        idToVar.add(v);
        return newId;
    }

    private static boolean computeOutIn(
        Node uNode,
        int u,
        Object2IntOpenHashMap<Node> idx,
        BitSet[] in,
        BitSet[] out,
        BitSet[] use,
        BitSet[] def,
        BitSet tmpOut,
        BitSet tmpIn
    ) {
        tmpOut.clear();
        for (Node succ : uNode.getSuccessors()) {
            int v = idx.getInt(succ);
            if (v != -1) {
                tmpOut.or(in[v]);
            }
        }

        boolean changed = false;
        if (!out[u].equals(tmpOut)) {
            out[u].clear();
            out[u].or(tmpOut);
            changed = true;
        }

        tmpIn.clear();
        tmpIn.or(tmpOut);
        tmpIn.andNot(def[u]);
        tmpIn.or(use[u]);
        if (!in[u].equals(tmpIn)) {
            in[u].clear();
            in[u].or(tmpIn);
            changed = true;
        }
        return changed;
    }

}
