package de.peeeq.wurstscript.types;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ModuleUse;
import de.peeeq.wurstscript.ast.NamedScope;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;

public class WurstTypeModuleUse extends WurstType {

	private final ModuleUse moduleUse;

	public WurstTypeModuleUse(ModuleUse moduleUse, List<WurstType> typeArgs) {
		this.moduleUse = moduleUse;
	}


	@Override
	public ImType imTranslateType(AstElement location) {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}


	@Override
	public boolean isSubtypeOfIntern(WurstType other, @Nullable AstElement location) {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}



	@Override
	public String getFullName() {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}

	@Override
	public boolean equals(@Nullable Object other) {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}


	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}

}
