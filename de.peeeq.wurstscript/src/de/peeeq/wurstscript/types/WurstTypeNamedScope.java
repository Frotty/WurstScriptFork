package de.peeeq.wurstscript.types;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.AstElementWithTypeParameters;
import de.peeeq.wurstscript.ast.ClassDef;
import de.peeeq.wurstscript.ast.InterfaceDef;
import de.peeeq.wurstscript.ast.ModuleDef;
import de.peeeq.wurstscript.ast.NameDef;
import de.peeeq.wurstscript.ast.NamedScope;
import de.peeeq.wurstscript.ast.TypeParamDef;
import de.peeeq.wurstscript.attributes.names.NameLink;
import de.peeeq.wurstscript.attributes.names.NameLinkType;

public abstract class WurstTypeNamedScope<T extends NamedScope & NameDef> extends WurstType {

	private final boolean isStaticRef;
	protected final TypeLink<T> typeLink;
	private final List<WurstType> typeParameters;
	
	
	public WurstTypeNamedScope(TypeLink<T> typeLink, List<WurstType> typeParameters, boolean isStaticRef) {
		this.typeLink = typeLink;
		this.isStaticRef = isStaticRef;
		this.typeParameters = typeParameters;
	}

	public WurstTypeNamedScope(TypeLink<T> typeLink, List<WurstType> typeParameters) {
		this.typeLink = typeLink;
		this.isStaticRef = false;
		this.typeParameters = typeParameters;
	}

	
	public WurstTypeNamedScope(TypeLink<T> typeLink, boolean isStaticRef) {
		this.typeLink = typeLink;
		this.isStaticRef = isStaticRef;
		this.typeParameters = Collections.emptyList();
	}

	@Override
	public String getName() {
		TypeLink<T> def = getTypeLink();
		return def.getName();
	}

	public TypeLink<T> getTypeLink() {
		return typeLink;
	}
	
	public T getDef(AstElement loc) {
		return typeLink.getDef(loc);
	}

	@Override
	public String getFullName() {
		return getName();
	}

	@Override
	public boolean isStaticRef() {
		return isStaticRef;
	}

	@Override
	public boolean isSubtypeOfIntern(WurstType obj, @Nullable AstElement location) {
		if (obj instanceof WurstTypeTypeParam) {
			return false;
		}
		if (obj instanceof WurstTypeNamedScope) {
			WurstTypeNamedScope<?> other = (WurstTypeNamedScope<?>) obj;
			if (other.getTypeLink().equals(getTypeLink())) {
				return checkTypeParametersEqual(getTypeParameters(), other.getTypeParameters(), location);
			}
		}
		return false;
	}

	public List<WurstType> getTypeParameters() {
		return typeParameters;
	}

	public WurstType getTypeParameterBinding(TypeParamDef def) {
		WurstType t = getTypeParamBounds().get(def);
		return t != null ? t : WurstTypeUnknown.instance();
	}
	
	
	@Nullable Map<TypeLink<TypeParamDef>, WurstType> cache_typeParamBounds;
	private Map<TypeLink<TypeParamDef>, WurstType> getTypeParamBounds() {
		Map<TypeLink<TypeParamDef>, WurstType> cache = cache_typeParamBounds;
		if (cache == null) {
			cache_typeParamBounds = cache = Maps.newLinkedHashMap();
			TypeLink<T> def = getTypeLink();
			List<TypeLink<TypeParamDef>> tps = def.getTypeParameters();
			for (int index = 0; index < typeParameters.size(); index++) {
				cache.put(tps.get(index), typeParameters.get(index));
			}
		}
		return cache;
	}
	
	protected String printTypeParams() {
		if (typeParameters.size() == 0) {
			return "";
		}
		String s = "<";
		for (int i=0; i<typeParameters.size(); i++) {
			if (i > 0) {
				s += ", ";
			}
			s += typeParameters.get(i).getName();
		}
		return s + ">";
	}
	
//	@Override
//	public  PscriptType replaceBoundTypeVars(PscriptType t) {
//		if (t instanceof PscriptTypeTypeParam) {
//			PscriptTypeTypeParam tpt = (PscriptTypeTypeParam) t;
//			PscriptType s = getTypeParamBounds().get(tpt.getDef());
//			if (s != null) {
//				return s;
//			}
//		} else if (t instanceof PscriptTypeNamedScope) {
//			PscriptTypeNamedScope ns = (PscriptTypeNamedScope) t;
//			return ns.replaceTypeVars(getTypeParamBounds());
//		}
//		return t;
//	}
	


	@Override
	public Map<TypeParamDef, WurstType> getTypeArgBinding(AstElement loc) {
		
		T def2 = getDef(loc);
		if (def2 instanceof AstElementWithTypeParameters) {
			AstElementWithTypeParameters def = (AstElementWithTypeParameters) def2;
			Map<TypeParamDef, WurstType> result = Maps.newLinkedHashMap();
			for (int i=0; i<typeParameters.size(); i++) {
				WurstType t = typeParameters.get(i);
				TypeParamDef tDef = def.getTypeParameters().get(i);
				result.put(tDef, t);
			}
			if (def instanceof ClassDef) {
				ClassDef c = (ClassDef) def;
				c.attrExtendedClass(); // to protect against the case where interface extends itself
				
				// type binding for extended class
				result.putAll(c.getExtendedClass().attrTyp()
						.getTypeArgBinding(loc));
				// type binding for implemented interfaces:
				for (WurstTypeInterface i : c.attrImplementedInterfaces()) {
					result.putAll(i.getTypeArgBinding(loc));
				}
			} else if (def instanceof InterfaceDef) {
				InterfaceDef i = (InterfaceDef) def;
				// type binding for implemented interfaces:
				for (WurstTypeInterface ii : i.attrExtendedInterfaces()) {
					result.putAll(ii.getTypeArgBinding(loc));
				}
			}
			normalizeTypeArgsBinding(result, loc);
			return result ;
		}
		return super.getTypeArgBinding(loc);
	}

	private void normalizeTypeArgsBinding(Map<TypeParamDef, WurstType> b, AstElement location) {
		List<TypeParamDef> keys = Lists.newArrayList(b.keySet());
		for (TypeParamDef p : keys) {
			WurstType t = b.get(p);
			b.put(p, normalizeType(t,b, location));
		}
	}

	private WurstType normalizeType(WurstType t, Map<TypeParamDef, WurstType> b, AstElement location) {
		t = t.normalize();
		if (t instanceof WurstTypeTypeParam) {
			WurstTypeTypeParam tp = (WurstTypeTypeParam) t;
			TypeParamDef tpDef = tp.getDef(location);
			if (b.containsKey(tpDef)) {
				WurstType t2 = b.get(tpDef);
				if (t != t2) {
					return normalizeType(t2, b, location);
				}
			}
		}
		return t;
	}

	@Override
	public WurstType setTypeArgs(Map<TypeParamDef, WurstType> typeParamBounds) {
		List<WurstType> newTypes = Lists.newArrayList();
		for (WurstType t : typeParameters) {
			newTypes.add(t.setTypeArgs(typeParamBounds));
		}
		return replaceTypeVars(newTypes);
	}

	abstract public WurstType replaceTypeVars(List<WurstType> newTypes);

	
	
	protected boolean checkTypeParametersEqual(List<WurstType> tps1, List<WurstType> tps2, @Nullable AstElement location) {
		if (tps1.size() != tps2.size()) {
			return false;
		}
		for (int i=0; i<tps1.size(); i++) {
			WurstType thisTp = tps1.get(i);
			WurstType otherTp = tps2.get(i);
			if (otherTp instanceof WurstTypeFreeTypeParam
					|| otherTp instanceof WurstTypeTypeParam) {
				// free type params can later be bound to the right type
				continue;
			}
			if (!thisTp.equalsType(otherTp, location)) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public boolean allowsDynamicDispatch() {
		// dynamic dispatch is possible if this is not a static reference
		return !isStaticRef();
	}
	
	@Override
	public void addMemberMethods(AstElement node, String name,
			List<NameLink> result) {
		NamedScope scope = getDef(node);
		if (scope instanceof ModuleDef) {
			// cannot access functions from outside of module 
		} else if (scope != null) {
			for (NameLink n : scope.attrNameLinks().get(name)) {
				WurstType receiverType = n.getReceiverType();
				if (n.getType() == NameLinkType.FUNCTION
						&& receiverType != null
						&& receiverType.isSupertypeOf(this, node)) {
					result.add(n.hidingPrivateAndProtected());
				}
			}
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (isStaticRef ? 1231 : 1237);
		result = prime * result + ((typeLink == null) ? 0 : typeLink.hashCode());
		result = prime * result + ((typeParameters == null) ? 0 : typeParameters.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WurstTypeNamedScope other = (WurstTypeNamedScope) obj;
		if (isStaticRef != other.isStaticRef)
			return false;
		if (typeLink == null) {
			if (other.typeLink != null)
				return false;
		} else if (!typeLink.equals(other.typeLink))
			return false;
		if (typeParameters == null) {
			if (other.typeParameters != null)
				return false;
		} else if (!typeParameters.equals(other.typeParameters))
			return false;
		return true;
	}
	
	
	
}