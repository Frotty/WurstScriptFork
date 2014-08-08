package de.peeeq.wurstscript.types;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;

public final class WurstTypeStaticTypeRef extends WurstType {

	private final WurstType base;
	
	public WurstTypeStaticTypeRef(WurstType base) {
		this.base = base;
	}
	
	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		return false;
	}

	@Override
	public String getName() {
		return base.getName();
	}

	@Override
	public String getFullName() {
		return "static reference to " + base.getFullName();
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return base.imTranslateType(location);
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return base.getDefaultValue(location);
	}
	
	@Override
	public WurstType dynamic() {
		return base;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((base == null) ? 0 : base.hashCode());
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
		WurstTypeStaticTypeRef other = (WurstTypeStaticTypeRef) obj;
		if (base == null) {
			if (other.base != null)
				return false;
		} else if (!base.equals(other.base))
			return false;
		return true;
	}

	

}
