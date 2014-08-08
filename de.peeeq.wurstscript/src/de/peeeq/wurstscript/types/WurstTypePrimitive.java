package de.peeeq.wurstscript.types;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.jassIm.ImSimpleType;
import de.peeeq.wurstscript.jassIm.JassIm;

public abstract class WurstTypePrimitive extends WurstType {
	
	private String name;
	private ImSimpleType imType;

	protected WurstTypePrimitive(String name) {
		this.name = name;
		imType = JassIm.ImSimpleType(name);
	}
	@Override
	public String getName() {
		return name;
	}
	@Override
	public String getFullName() {
		return name;
	}
	
	@Override
	public ImSimpleType imTranslateType(@Nullable AstElement location) {
		return imType;
	}
	
	public ImSimpleType imTranslateType() {
		return imType;
	}
	
	@Override
	public final boolean equals(@Nullable Object other) {
		return this == other;
	}
	
	@Override
	public final int hashCode() {
		return System.identityHashCode(this);
	}
	
}
