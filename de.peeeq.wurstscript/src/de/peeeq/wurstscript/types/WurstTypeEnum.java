package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.EnumDef;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeEnum extends WurstTypeNamedScope<EnumDef> {


	public WurstTypeEnum(boolean isStaticRef, EnumDef edef) {
		super(TypeLink.to(edef), isStaticRef);
	}
	
	public WurstTypeEnum(boolean isStaticRef, TypeLink<EnumDef> edef) {
		super(edef, isStaticRef);
	}

	@Override
	public String getName() {
		return typeLink.getName();
	}
	
	@Override
	public WurstType dynamic() {
		return new WurstTypeEnum(false, typeLink);
	}

	@Override
	public WurstType replaceTypeVars(List<WurstType> newTypes) {
		return this;
	}

	
	@Override
	public ImType imTranslateType(AstElement location) {
		return TypesHelper.imInt();
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImIntVal(0);
	}
	

	@Override
	public boolean isCastableToInt() {
		return true;
	}
	

}
