package de.peeeq.wurstscript.intermediateLang;

import de.peeeq.wurstscript.utils.Utils;

/**
 * a binary expression with a constant on the right like
 * 
 * set x = y + 1
 *
 */
public class ILsetBinaryCR extends ILStatementSet {

	private Iloperator op;
	private ILvar left;
	private ILconst right;

	public ILsetBinaryCR(ILvar resultVar, ILvar left, Iloperator op, ILconst right) {
		super(resultVar);
		this.op = op;
		this.left = left;
		this.right = right;
	}


	public Iloperator getOp() {
		return op;
	}

	public ILvar getLeft() {
		return left;
	}

	public ILconst getRight() {
		return right;
	}


	@Override
	public void printJass(StringBuilder sb, int indent) {
		ILsetExpr temp = new ILsetExpr(resultVar, new ILexprBinary(left, op, right));
		temp.printJass(sb, indent);
	}
	
	

}