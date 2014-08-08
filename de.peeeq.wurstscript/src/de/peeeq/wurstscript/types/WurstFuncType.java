package de.peeeq.wurstscript.types;

import java.util.Arrays;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public final class WurstFuncType extends WurstType {


	private final WurstType returnType;
	private final WurstType[] paramTypes;

	public WurstFuncType(WurstType returnType, WurstType ... paramTypes ) {
		this.returnType = returnType;
		this.paramTypes = paramTypes;
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		if (! (other instanceof WurstFuncType)) {
			return false;
		}
//		PsciptFuncType f = (PsciptFuncType) other;
		
		// TODO PsciptFuncType
		return false;
	}

	@Override
	public String getName() {
		String result = "function (";
		for (int i=0; i<paramTypes.length; i++) {
			if (i>0) {
				result += ",";
			}
			result += paramTypes[i].getName();
		}
		result += ":"+returnType.getName();
		return result ;
	}

	@Override
	public String getFullName() {
		String result = "function(";
		for (int i=0; i<paramTypes.length; i++) {
			if (i>0) {
				result += ",";
			}
			result += paramTypes[i].getFullName();
		}
		result += "):"+returnType.getFullName();
		return result ;
	}


	@Override
	public ImType imTranslateType(AstElement location) {
		return JassIm.ImSimpleType("code");
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImNull();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Arrays.hashCode(paramTypes);
		result = prime * result + ((returnType == null) ? 0 : returnType.hashCode());
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
		WurstFuncType other = (WurstFuncType) obj;
		if (!Arrays.equals(paramTypes, other.paramTypes))
			return false;
		if (returnType == null) {
			if (other.returnType != null)
				return false;
		} else if (!returnType.equals(other.returnType))
			return false;
		return true;
	}


	
	
	
}
