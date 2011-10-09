package de.peeeq.wurstscript.intermediateLang;

public abstract class ILStatementSet extends ILStatement {

	protected ILvar resultVar;
	
	public ILStatementSet(ILvar resultVar) {
		this.resultVar = resultVar;
	}

	public ILvar getResultVar() {
		return resultVar;
	}
}