package de.peeeq.wurstscript.types;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;

public class WurstTypeUnion extends WurstType {

	WurstType typeA;
	WurstType typeB;
	
	private WurstTypeUnion(WurstType typeA, WurstType typeB) {
		this.typeA = typeA;
		this.typeB = typeB;
	}
	
	public static WurstType create(WurstType a, WurstType b) {
		if (a instanceof WurstTypeUnknown) return b;
		if (b instanceof WurstTypeUnknown) return a;
		// TODO simplify types
//		if (a.isSubtypeOf(b, null)) return b;
//		if (b.isSubtypeOf(a, null)) return a;
		return new WurstTypeUnion(a, b);
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, @Nullable AstElement location) {
		return typeA.isSubtypeOf(other, location)
				&& typeB.isSubtypeOf(other, location);
	}

	@Override
	public String getName() {
		return typeA.getName() + " or " + typeB.getName();
	}

	@Override
	public String getFullName() {
		return typeA.getFullName() + " or " + typeB.getFullName();
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return typeA.imTranslateType(location);
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return typeA.getDefaultValue(location);
	}

	public WurstType getTypeA() {
		return typeA;
	}

	public WurstType getTypeB() {
		return typeB;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((typeA == null) ? 0 : typeA.hashCode());
		result = prime * result + ((typeB == null) ? 0 : typeB.hashCode());
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
		WurstTypeUnion other = (WurstTypeUnion) obj;
		if (typeA == null) {
			if (other.typeA != null)
				return false;
		} else if (!typeA.equals(other.typeA))
			return false;
		if (typeB == null) {
			if (other.typeB != null)
				return false;
		} else if (!typeB.equals(other.typeB))
			return false;
		return true;
	}

	
	
}
