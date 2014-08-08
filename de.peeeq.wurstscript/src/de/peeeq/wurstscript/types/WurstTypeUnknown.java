package de.peeeq.wurstscript.types;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeUnknown extends WurstType {

	private static final WurstTypeUnknown instance = new WurstTypeUnknown("unknown");

	private String name = "unknown";

	public WurstTypeUnknown(String name) {
		this.name = name;
		try {
			throw new Error("unknown type");
		} catch (Error e) {
		}
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		return false;
	}


	@Override
	public String getName() {
		if (name.equals("empty")) {
			return "missing expression";
		}
		return "'unknown type'\n(the type " + name + 
				" could not be found, the containing package might not be imported)";
	}

	@Override
	public String getFullName() {
		return getName();
	}

	public static WurstTypeUnknown instance() {
		return instance;
	}


	@Override
	public ImType imTranslateType(AstElement location) {
		throw new Error("not implemented");
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImNoExpr();
	}
	
	@Override
	public boolean equals(@Nullable Object other) {
		return this == other;
	}
	
	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}

}
