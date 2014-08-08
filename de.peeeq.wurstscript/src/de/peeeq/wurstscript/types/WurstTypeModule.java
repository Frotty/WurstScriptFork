package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ModuleDef;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeModule extends WurstTypeNamedScope<ModuleDef> {


	public WurstTypeModule(ModuleDef moduleDef, boolean isStaticRef) {
		super(TypeLink.to(moduleDef), isStaticRef);
	}

	public WurstTypeModule(ModuleDef moduleDef, List<WurstType> newTypes) {
		super(TypeLink.to(moduleDef), newTypes);
	}
	
	public WurstTypeModule(TypeLink<ModuleDef> moduleDef, boolean isStaticRef) {
		super(moduleDef, isStaticRef);
	}

	public WurstTypeModule(TypeLink<ModuleDef> moduleDef, List<WurstType> newTypes) {
		super(moduleDef, newTypes);
	}
	
	@Override
	public boolean isSubtypeOfIntern(WurstType obj, AstElement location) {
		if (super.isSubtypeOfIntern(obj, location)) {
			return true;
		}
		if (obj instanceof WurstTypeModuleInstanciation) {
			WurstTypeModuleInstanciation n = (WurstTypeModuleInstanciation) obj;
			return n.isParent(this, location);
		}
		return false;
	}

	
	@Override
	public String getName() {
		return typeLink.getName() + printTypeParams() + " (module)";
	}

	@Override
	public WurstType dynamic() {
		if (isStaticRef()) {
			return new WurstTypeModule(typeLink, false);
		}
		return this;
	}

	@Override
	public WurstType replaceTypeVars(List<WurstType> newTypes) {
		return new WurstTypeModule(typeLink, newTypes);
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return TypesHelper.imInt();
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImNull();
	}
	
	@Override
	public boolean isCastableToInt() {
		return true;
	}
}
