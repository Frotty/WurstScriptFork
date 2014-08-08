package de.peeeq.wurstscript.types;

import java.util.List;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ClassDef;
import de.peeeq.wurstscript.ast.StructureDef;
import de.peeeq.wurstscript.ast.TypeExpr;
import de.peeeq.wurstscript.jassIm.ImExprOpt;
import de.peeeq.wurstscript.jassIm.ImType;
import de.peeeq.wurstscript.jassIm.JassIm;


public class WurstTypeClass extends WurstTypeClassOrInterface<ClassDef> {

	public WurstTypeClass(ClassDef classDef, List<WurstType> typeParameters, boolean staticRef) {
		super(TypeLink.to(classDef), typeParameters, staticRef);
	}
	
	public WurstTypeClass(TypeLink<ClassDef> classDef, List<WurstType> typeParameters, boolean staticRef) {
		super(classDef, typeParameters, staticRef);
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType obj, AstElement location) {
		if (super.isSubtypeOfIntern(obj, location)) {
			return true;
		}
		ClassDef c = typeLink.getDef(location);
		if (obj instanceof WurstTypeInterface) {
			WurstTypeInterface pti = (WurstTypeInterface) obj;
			for (WurstTypeInterface implementedInterface : c.attrImplementedInterfaces()) {
				if (implementedInterface.setTypeArgs(getTypeArgBinding(location)).isSubtypeOf(pti, location)) {
					return true;
				}
			}
		}
		if (obj instanceof WurstTypeModuleInstanciation) {
			WurstTypeModuleInstanciation n = (WurstTypeModuleInstanciation) obj;
			return n.isParent(this, location);
		}
		if (c.getExtendedClass() instanceof TypeExpr) {
			TypeExpr extendedClass = (TypeExpr) c.getExtendedClass();
			WurstType superType = extendedClass.attrTyp();
			return superType.isSubtypeOf(obj, location);
		}
		return false;
	}
	
	
	@Override
	public String getName() {
		return getTypeLink().getName() + printTypeParams();
	}
	
	@Override
	public WurstType dynamic() {
		return new WurstTypeClass(typeLink, getTypeParameters(), false);
	}

	@Override
	public WurstType replaceTypeVars(List<WurstType> newTypes) {
		return new WurstTypeClass(typeLink, newTypes, isStaticRef());
	}

	@Override
	public ImType imTranslateType(AstElement location) {
		return TypesHelper.imInt();
	}

	@Override
	public ImExprOpt getDefaultValue(AstElement location) {
		return JassIm.ImIntVal(0);
	}

	
	
	
	
}
