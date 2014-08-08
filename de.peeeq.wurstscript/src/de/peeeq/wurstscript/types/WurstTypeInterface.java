package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.InterfaceDef;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeInterface extends WurstTypeClassOrInterface<InterfaceDef> {


//	public PscriptTypeInterface(InterfaceDef interfaceDef, boolean staticRef) {
//		super(staticRef);
//		if (interfaceDef == null) throw new IllegalArgumentException();
//		this.interfaceDef = interfaceDef;
//	}

	public WurstTypeInterface(InterfaceDef interfaceDef, List<WurstType> newTypes, boolean isStaticRef) {
		super(TypeLink.to(interfaceDef), newTypes, isStaticRef);
	}
	
	public WurstTypeInterface(InterfaceDef interfaceDef, List<WurstType> newTypes) {
		super(TypeLink.to(interfaceDef), newTypes);
	}
	
	public WurstTypeInterface(TypeLink<InterfaceDef> interfaceDef, List<WurstType> newTypes, boolean isStaticRef) {
		super(interfaceDef, newTypes, isStaticRef);
	}

	public WurstTypeInterface(TypeLink<InterfaceDef> interfaceDef, List<WurstType> newTypes) {
		super(interfaceDef, newTypes);
	}
	
	@Override
	public String getName() {
		return typeLink.getName() + printTypeParams();
	}
	
	@Override
	public WurstType dynamic() {
		if (isStaticRef()) {
			return new WurstTypeInterface(typeLink, getTypeParameters(), false);
		}
		return this;
	}

	@Override
	public WurstType replaceTypeVars(List<WurstType> newTypes) {
		return new WurstTypeInterface(typeLink, newTypes);
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType other, AstElement location) {
		if (super.isSubtypeOfIntern(other, location)) {
			return true;
		}
		
		if (other instanceof WurstTypeInterface) {
			WurstTypeInterface other2 = (WurstTypeInterface) other;
			if (typeLink.equals(other2.typeLink)) {
				// same interface -> check if type params are equal
				return checkTypeParametersEqual(getTypeParameters(), other2.getTypeParameters(), location);
			} else {
				InterfaceDef interfaceDef = typeLink.getDef(location);
				// test super interfaces:
				for (WurstTypeInterface extended : interfaceDef.attrExtendedInterfaces() ) {
					if (extended.isSubtypeOf(other, location)) {
						return true;
					}
				}
			}
		}
		return false;
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
