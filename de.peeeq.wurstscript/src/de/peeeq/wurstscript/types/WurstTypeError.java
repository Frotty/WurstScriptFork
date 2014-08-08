package de.peeeq.wurstscript.types;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;

public final class WurstTypeError extends WurstType {

	private final String msg;

	public WurstTypeError(String msg) {
		this.msg = msg;
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		return false;
	}


	@Override
	public String getName() {
		return "Error: " + msg;
	}

	@Override
	public String getFullName() {
		return getName();
	}


	@Override
	public ImType imTranslateType(AstElement location) {
		throw new Error("not implemented");
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		throw new Error("not implemented");
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((msg == null) ? 0 : msg.hashCode());
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
		WurstTypeError other = (WurstTypeError) obj;
		if (msg == null) {
			if (other.msg != null)
				return false;
		} else if (!msg.equals(other.msg))
			return false;
		return true;
	}

	
	
}
