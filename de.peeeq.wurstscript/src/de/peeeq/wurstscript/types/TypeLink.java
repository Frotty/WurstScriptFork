package de.peeeq.wurstscript.types;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import de.peeeq.wurstscript.ast.AstElement;
import de.peeeq.wurstscript.ast.CompilationUnit;
import de.peeeq.wurstscript.ast.NameDef;
import de.peeeq.wurstscript.ast.TypeParamDef;
import de.peeeq.wurstscript.ast.WurstModel;
import de.peeeq.wurstscript.utils.Utils;

/**
 * represents a link to a type
 * a link is represented by a path from the root of the AST to the
 * definition of the type
 */
public class TypeLink<T extends NameDef> {
	
	private final ImmutableList<AstPathNode> path;
	private final String name;
	private final Class<? extends T> clazz;
	
	private TypeLink(ImmutableList<AstPathNode> path, String name, Class<? extends T> clazz) {
		this.path = path;
		this.name = name;
		this.clazz = clazz;
	}
	

	public static <T extends NameDef> TypeLink<T> to(T def) {
		List<AstPathNode> path = Lists.newArrayList();
		
		buildPathTo(path, def);
		
		ImmutableList.Builder<AstPathNode> r = new ImmutableList.Builder<>();
		for (int i=path.size()-1; i>=0; i--) {
			r.add(path.get(i));
		}
		@SuppressWarnings("unchecked") // I guess this should be safe, def is of type T so def.getClass should be of this type
		Class<? extends T> c = (Class<? extends T>) def.getClass();
		TypeLink<T> result = new TypeLink<T>(r.build(), def.getName(), c);
		if (result.getDef(def) != def) {
			throw new RuntimeException("typelink not constructed correctly for " + Utils.printElement(def) + ", got " + Utils.printElement(result.getDef(def)));
		}
		
		return result;
	}
	
	private static void buildPathTo(List<AstPathNode> path, AstElement e) {
		
		AstElement parent = e.getParent();
		if (parent == null) {
			return;
		}
		
		// TODO add some special cases for uniquely named elements
		boolean found = false;
		for (int i=0; i<parent.size(); i++) {
			if (parent.get(i) == e) {
				path.add(AstPathNode.selectChild(i, Utils.printElement(parent) + " -> " + Utils.printElement(e)));
				found = true;
				break;
			}
		}
		if (!found) {
			throw new RuntimeException("not found: " + e);
		}
		
		
		
		buildPathTo(path, parent);
	}

	public String getName() {
		return name;
	}

	public T getDef(AstElement location) {
		WurstModel model = location.getModel();
		Preconditions.checkNotNull(model);
		AstElement e = model;
		for (int i=0; i<path.size(); i++) {
			e = path.get(i).follow(e);
		}
		// TODO type check
		if (clazz.isAssignableFrom(e.getClass())) {
			@SuppressWarnings("unchecked") // dynamic isAssignableFrom check ensures that this cast is correct
			T r = (T) e;
			return r;
		}
		throw new RuntimeException("Strange result: " + Utils.printElement(e));
	}
	
		
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((path == null) ? 0 : path.hashCode());
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
		TypeLink other = (TypeLink) obj;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (path == null) {
			if (other.path != null)
				return false;
		} else if (!path.equals(other.path))
			return false;
		return true;
	}


	public List<TypeLink<TypeParamDef>> getTypeParameters() {
		// TODO Auto-generated method stub
		throw new Error("not implemented");
	}
	
}
