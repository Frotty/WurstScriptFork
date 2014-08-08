package de.peeeq.wurstscript.types;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.TupleDef;
import de.peeeq.wurstscript.ast.WParameter;
import de.peeeq.wurstscript.jassIm.ImExpr;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImExprs;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeTuple extends WurstType {

	TypeLink<TupleDef> tupleDef;

	public WurstTypeTuple(TupleDef tupleDef) {
		Preconditions.checkNotNull(tupleDef);
		this.tupleDef = TypeLink.to(tupleDef);
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, @Nullable AstElement location) {
		if (other instanceof WurstTypeTuple) {
			WurstTypeTuple otherTuple = (WurstTypeTuple) other;
			return tupleDef == otherTuple.tupleDef;
		}
		return false;
	}
	

	public TupleDef getTupleDef(AstElement location) {
		return tupleDef.getDef(location);
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
	public ImType imTranslateType(AstElement location) {
		List<ImType> types = Lists.newArrayList();
		List<String> names = Lists.newArrayList();
		for (WParameter p : getTupleDef(location).getParameters()) {
			ImType pt = p.attrTyp().imTranslateType(location);
			types.add(pt);
			names.add(p.getName());
		}
		return JassIm.ImTupleType(types, names);
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		ImExprs exprs = JassIm.ImExprs();
		for (WParameter p : getTupleDef(location).getParameters()) {
			exprs.add((ImExpr) p.attrTyp().getDefaultValue(location));
		}
		return JassIm.ImTupleExpr(exprs);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((tupleDef == null) ? 0 : tupleDef.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WurstTypeTuple other = (WurstTypeTuple) obj;
		if (tupleDef == null) {
			if (other.tupleDef != null)
				return false;
		} else if (!tupleDef.equals(other.tupleDef))
			return false;
		return true;
	}

	public TupleDef getDef(AstElement location) {
		return tupleDef.getDef(location);
	}
	
	
	
}
