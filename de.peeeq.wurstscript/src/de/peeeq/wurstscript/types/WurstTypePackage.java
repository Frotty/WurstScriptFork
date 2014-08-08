package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.NamedScope;
import de.peeeq.wurstscript.ast.WPackage;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;


public class WurstTypePackage extends WurstTypeNamedScope<WPackage> {


	public WurstTypePackage(WPackage pack) {
		super(TypeLink.to(pack), true);
	}

	public WurstTypePackage(TypeLink<WPackage> pack) {
		super(pack, true);
	}
	
	@Override
	public String getName() {
		return typeLink.getName() + printTypeParams() + " (package)";
	}
	
	@Override
	public WurstType dynamic() {
		return this;
	}

	@Override
	public WurstType replaceTypeVars(List<WurstType> newTypes) {
		return this;
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		throw new Error("not implemented");
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		throw new Error("not implemented");
	}

}
