package de.peeeq.wurstscript.types;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.ast.*;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.utils.Utils;
import org.eclipse.jdt.annotation.Nullable;

import java.util.List;


public class WurstTypeTuple extends WurstType {

    private TupleDef tupleDef;


    public WurstTypeTuple(TupleDef tupleDef) {
        Preconditions.checkNotNull(tupleDef);
        this.tupleDef = tupleDef;
    }

    @Override
    VariableBinding matchAgainstSupertypeIntern(WurstType other, @Nullable Element location, VariableBinding mapping, VariablePosition variablePosition) {
        if (other instanceof WurstTypeTuple) {
            WurstTypeTuple otherTuple = (WurstTypeTuple) other;
            if (tupleDef == otherTuple.tupleDef) {
                return mapping;
            }
        }
        return null;
    }


    public TupleDef getTupleDef() {
        return tupleDef;
    }

    @Override
    public String getName() {
        return tupleDef.getName();
    }

    @Override
    public String getFullName() {
        return getName();
    }


    @Override
    public ImType imTranslateType() {
        List<ImType> types = Lists.newArrayList();
        List<String> names = Lists.newArrayList();
        for (WParameter p : tupleDef.getParameters()) {
            ImType pt = p.attrTyp().imTranslateType();
            types.add(pt);
            names.add(p.getName());
        }
        return JassIm.ImTupleType(types, names);
    }

    @Override
    public ImExprOpt getDefaultValue() {
        ImExprs exprs = JassIm.ImExprs();
        for (WParameter p : tupleDef.getParameters()) {
            exprs.add((ImExpr) p.attrTyp().getDefaultValue());
        }
        return JassIm.ImTupleExpr(exprs);
    }

    public int getTupleIndex(VarDef varDef) {
        WParameter v = (WParameter) varDef;
        int index = tupleDef.getParameters().indexOf(v);
        if (index < 0) {
            throw new CompileError(varDef.getSource(), "Could not determine tuple index of " + Utils.printElementWithSource(varDef) + " in tuple " + this);
        }
        return index;
    }
}
