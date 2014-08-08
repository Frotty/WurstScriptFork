package de.peeeq.wurstscript.types;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;

public final class WurstNativeType extends WurstType {

	private final String name;
	private final WurstType superType;

	
	public WurstNativeType(String name, WurstType superType) {
		this.name = name;
		this.superType = superType;
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		if (other instanceof WurstNativeType) {
			System.out.println(this + " <: " + other);
			return ((WurstNativeType)other).name.equals(name)
				|| superType.isSubtypeOfIntern(other, location);
		}
		return superType.isSubtypeOfIntern(other, location);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getFullName() {
		return name;
	}

	public static WurstNativeType instance(String name, WurstType superType) {
		return new WurstNativeType(name, superType);
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return JassIm.ImSimpleType(name);
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImNull();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((superType == null) ? 0 : superType.hashCode());
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
		WurstNativeType other = (WurstNativeType) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (superType == null) {
			if (other.superType != null)
				return false;
		} else if (!superType.equals(other.superType))
			return false;
		return true;
	}
	
	

}
