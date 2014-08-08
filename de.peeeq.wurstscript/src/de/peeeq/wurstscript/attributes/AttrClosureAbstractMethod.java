package de.peeeq.wurstscript.attributes;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.Multimap;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.ExprClosure;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.attributes.names.NameLinkType;
import de.peeeq.wurstscript.types.FunctionSignature;
import de.peeeq.wurstscript.types.WurstType;
import de.peeeq.wurstscript.types.WurstTypeClassOrInterface;

public class AttrClosureAbstractMethod {

	public static @Nullable NameLink calculate(ExprClosure e) {
		WurstType expected = e.attrExpectedTyp();
		if (expected instanceof WurstTypeClassOrInterface) {
			WurstTypeClassOrInterface<?> ct = (WurstTypeClassOrInterface<?>) expected;
			return findAbstractMethod(ct.getDef(e).attrNameLinks());
		}
		return null;
	}


	public static @Nullable FunctionSignature getAbstractMethodSignature(WurstType type, AstElement location) {
		if (type instanceof WurstTypeClassOrInterface) {
			WurstTypeClassOrInterface<?> ct = (WurstTypeClassOrInterface<?>) type;
			NameLink abstractMethod = findAbstractMethod(ct.getDef(location).attrNameLinks());
			if (abstractMethod == null) {
				return null;
			}
			FunctionSignature sig = FunctionSignature.fromNameLink(abstractMethod);
			sig = sig.setTypeArgs(ct.getTypeArgBinding(location));
			return sig;
		}
		return null;
	}
	
	private static @Nullable NameLink findAbstractMethod(Multimap<String, NameLink> nameLinks) {
		NameLink abstractMethod = null; 
		for (NameLink nl : nameLinks.values()) {
			if (nl.getType() == NameLinkType.FUNCTION
					&& nl.getNameDef().attrIsAbstract()) {
				if (abstractMethod != null) {
					// there is more than one abstract function
					// --> closure cannot implement this
					return null;
				}
				abstractMethod = nl;
			}
		}
		return abstractMethod;
	}
	
}
