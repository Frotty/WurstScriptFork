package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.StructureDef;

public abstract class WurstTypeClassOrInterface<T extends StructureDef> extends WurstTypeNamedScope<T> {

	public WurstTypeClassOrInterface(TypeLink<T> typeLink, List<WurstType> typeParameters,
			boolean isStaticRef) {
		super(typeLink, typeParameters, isStaticRef);
	}

	public WurstTypeClassOrInterface(TypeLink<T> typeLink, List<WurstType> newTypes) {
		super(typeLink, newTypes);
	}

	
	
	@Override
	public boolean canBeUsedInInstanceOf() {
		return true;
	}
	

	@Override
	public boolean isCastableToInt() {
		return true;
	}
}
