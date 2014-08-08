package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ModuleInstanciation;
import de.peeeq.wurstscript.ast.NamedScope;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeModuleInstanciation extends WurstTypeNamedScope<ModuleInstanciation> {

	public WurstTypeModuleInstanciation(ModuleInstanciation moduleInst, boolean isStaticRef) {
		super(TypeLink.to(moduleInst), isStaticRef);
	}

	public WurstTypeModuleInstanciation(ModuleInstanciation moduleInst, List<WurstType> newTypes) {
		super(TypeLink.to(moduleInst), newTypes);
	}
	
	public WurstTypeModuleInstanciation(TypeLink<ModuleInstanciation> moduleInst, boolean isStaticRef) {
		super(moduleInst, isStaticRef);
	}

	public WurstTypeModuleInstanciation(TypeLink<ModuleInstanciation> moduleInst, List<WurstType> newTypes) {
		super(moduleInst, newTypes);
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

	/**
	 * check if n is a parent of this
	 */
	boolean isParent(WurstTypeNamedScope<?> n, AstElement loc) {
		NamedScope ns = typeLink.getDef(loc);
		while (true) {
			ns = ns.getParent().attrNearestNamedScope();
			if (ns == null) {
				return false;
			}
			if (ns == n.getDef(loc)) {
				return true;
			}
		}
	}
	
	
	@Override
	public String getName() {
		return typeLink.getName() + printTypeParams() + " (module instanciation)";
	}
	
	@Override
	public WurstType dynamic() {
		if (isStaticRef()) {
			return new WurstTypeModuleInstanciation(typeLink, false);
		}
		return this;
	}

	@Override
	public WurstType replaceTypeVars(List<WurstType> newTypes) {
		return new WurstTypeModuleInstanciation(typeLink, newTypes);
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return TypesHelper.imInt();
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImNull();
	}

}
