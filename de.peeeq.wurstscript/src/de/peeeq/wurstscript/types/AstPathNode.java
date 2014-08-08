package de.peeeq.wurstscript.types;

import de.peeeq.wurstscript.ast.AstElement;

public class AstPathNode {

	int selectedNode;
	String debugInfo;
	
	private AstPathNode(int selectedNode, String debugInfo) {
		this.selectedNode = selectedNode;
		this.debugInfo = debugInfo;
	}

	public static AstPathNode selectChild(int i, String debugInfo) {
		return new AstPathNode(i, debugInfo);
	}

	public AstElement follow(AstElement e) {
		return e.get(selectedNode);
	}
	
	@Override
	public String toString() {
		return "> " + selectedNode + "( " + debugInfo + ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + selectedNode;
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
		AstPathNode other = (AstPathNode) obj;
		if (selectedNode != other.selectedNode)
			return false;
		return true;
	}
	
	
	

}
