package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.TypeParamDef;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;

public final class WurstTypeBoundTypeParam extends WurstType {

	
	private final TypeLink<TypeParamDef> typeParamDef;
	private final WurstType baseType;

	public WurstTypeBoundTypeParam(TypeLink<TypeParamDef> def, WurstType baseType) {
		this.typeParamDef = def;
		this.baseType = baseType;
	}
	
	public WurstTypeBoundTypeParam(TypeParamDef def, WurstType baseType) {
		this.typeParamDef = TypeLink.to(def);
		this.baseType = baseType;
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		return baseType.isSubtypeOfIntern(other, location);
	}

	@Override
	public String getName() {
		return baseType.getName();
	}

	@Override
	public String getFullName() {
		return typeParamDef.getName() + "<--" + baseType.getFullName();
	}


	public WurstType getBaseType() {
		return baseType;
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return baseType.imTranslateType(location);
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImIntVal(0);
	}

	
	@Override
	public WurstType dynamic() {
		return baseType.dynamic();
	}
	
	@Override
	public boolean canBeUsedInInstanceOf() {
		return baseType.canBeUsedInInstanceOf();
	}
	
	@Override
	public boolean allowsDynamicDispatch() {
		return baseType.allowsDynamicDispatch();
	}
	
	@Override
	public void addMemberMethods(AstElement node, String name,
			List<NameLink> result) {
		baseType.addMemberMethods(node, name, result);
	}
	
	@Override
	public boolean isStaticRef() {
		return baseType.isStaticRef();
	}
	
	@Override
	public boolean isCastableToInt() {
		return true; // because baseType must always be castable to int 
		//return baseType.isCastableToInt();
	}
	
	@Override
	public WurstType normalize() {
		return baseType.normalize();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((baseType == null) ? 0 : baseType.hashCode());
		result = prime * result + ((typeParamDef == null) ? 0 : typeParamDef.hashCode());
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
		WurstTypeBoundTypeParam other = (WurstTypeBoundTypeParam) obj;
		if (baseType == null) {
			if (other.baseType != null)
				return false;
		} else if (!baseType.equals(other.baseType))
			return false;
		if (typeParamDef == null) {
			if (other.typeParamDef != null)
				return false;
		} else if (!typeParamDef.equals(other.typeParamDef))
			return false;
		return true;
	}
	
	
	
}
