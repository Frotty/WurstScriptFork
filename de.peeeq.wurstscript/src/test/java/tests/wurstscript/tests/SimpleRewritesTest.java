package tests.wurstscript.tests;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import de.peeeq.wurstscript.RunArgs;
import de.peeeq.wurstscript.WurstOperator;
import de.peeeq.wurstscript.ast.Ast;
import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.intermediatelang.optimizer.SimpleRewrites;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.translation.imoptimizer.ImOptimizer;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.types.TypesHelper;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.testng.Assert.*;

public class SimpleRewritesTest extends WurstScriptTest {

    private static final Element TRACE = Ast.NoExpr();

    private ImTranslator translatorWithReturn(ImExpr returnExpr, ImType returnType) {
        ImTranslator translator = new ImTranslator(Ast.WurstModel(), true, new RunArgs());
        ImProg prog = translator.getImProg();
        ImFunction function = JassIm.ImFunction(
            TRACE,
            "testFunc",
            JassIm.ImTypeVars(),
            JassIm.ImVars(),
            returnType,
            JassIm.ImVars(),
            JassIm.ImStmts(JassIm.ImReturn(TRACE, returnExpr)),
            Collections.emptyList()
        );
        prog.getFunctions().add(function);
        return translator;
    }

    @Test
    public void booleanBinaryFoldCountsRewrite() {
        ImOperatorCall op = JassIm.ImOperatorCall(
            WurstOperator.AND,
            JassIm.ImExprs(JassIm.ImBoolVal(true), JassIm.ImBoolVal(false))
        );
        ImTranslator translator = translatorWithReturn(op, TypesHelper.imBool());

        SimpleRewrites pass = new SimpleRewrites();
        int rewrites = pass.optimize(translator);

        assertEquals(rewrites, 1, "constant folding should count as a rewrite");
        ImFunction function = translator.getImProg().getFunctions().get(0);
        ImReturn ret = (ImReturn) function.getBody().get(0);
        assertTrue(ret.getReturnValue() instanceof ImBoolVal);
        assertFalse(((ImBoolVal) ret.getReturnValue()).getValB());
    }

    @Test
    public void divisionByZeroIsNotRewritten() {
        ImOperatorCall div = JassIm.ImOperatorCall(
            WurstOperator.DIV_INT,
            JassIm.ImExprs(JassIm.ImIntVal(5), JassIm.ImIntVal(0))
        );
        ImTranslator translator = translatorWithReturn(div, TypesHelper.imInt());

        SimpleRewrites pass = new SimpleRewrites();
        int rewrites = pass.optimize(translator);

        assertEquals(rewrites, 0, "division by zero must not be folded");
        ImFunction function = translator.getImProg().getFunctions().get(0);
        ImReturn ret = (ImReturn) function.getBody().get(0);
        assertTrue(ret.getReturnValue() instanceof ImOperatorCall);
        ImOperatorCall callAfter = (ImOperatorCall) ret.getReturnValue();
        assertEquals(callAfter.getOp(), WurstOperator.DIV_INT);
    }

    @Test
    public void repeatedPassesDontDoubleCount() {
        ImOperatorCall op = JassIm.ImOperatorCall(
            WurstOperator.OR,
            JassIm.ImExprs(JassIm.ImBoolVal(false), JassIm.ImBoolVal(true))
        );
        ImTranslator translator = translatorWithReturn(op, TypesHelper.imBool());

        SimpleRewrites firstPass = new SimpleRewrites();
        int first = firstPass.optimize(translator);
        assertEquals(first, 1, "first pass should detect a rewrite");

        SimpleRewrites secondPass = new SimpleRewrites();
        int second = secondPass.optimize(translator);
        assertEquals(second, 0, "once rewritten, the pass should be idempotent");

        ImFunction function = translator.getImProg().getFunctions().get(0);
        ImReturn ret = (ImReturn) function.getBody().get(0);
        assertTrue(ret.getReturnValue() instanceof ImBoolVal);
        assertTrue(((ImBoolVal) ret.getReturnValue()).getValB());
    }

    private String readOptimized(String methodName) throws IOException {
        File file = new File("./test-output/" + getClass().getSimpleName() + "_" + methodName + "_opt.j");
        assertTrue(file.exists(), "Expected optimized output file to exist: " + file.getPath());
        return Files.toString(file, Charsets.UTF_8);
    }

    @Test
    public void test_complex_arithmetic_chain() throws IOException {
        ImOptimizer.localOptRounds = 10;
        test().lines(
            "package test",
            "   native print(int msg)",
            "   int stored = 0",
            "   function compute(int pid) returns int",
            "           stored = (5 + 9) * 16 + pid + 1",
            "           return stored",
            "   init",
            "           print(compute(1))",
            "endpackage");

        String output = readOptimized("test_complex_arithmetic_chain");
        assertFalse(output.contains("5 + 9"), "constants should be folded instead of left in the output");
        assertFalse(output.contains("* 16"), "multiplication by literal should be folded away");
        assertTrue(output.contains("print($e2)") ,
            "expected the folded constant to be combined with pid in the stored assignment");
        ImOptimizer.localOptRounds = 1;
    }

    @Test
    public void test_boolean_simplification() throws IOException {
        ImOptimizer.localOptRounds = 10;
        test().lines(
            "package test",
            "   native print(boolean msg)",
            "   boolean stored = false",
            "   function boolSimplify(boolean flag) returns boolean",
            "           stored = (flag and true) or false",
            "           print(stored)",
            "           return stored",
            "   init",
            "           boolSimplify(true)",
            "endpackage");

        String output = readOptimized("test_boolean_simplification");
        assertFalse(output.contains("and true"), "true conjunction should collapse to the other operand");
        assertFalse(output.contains("or false"), "false disjunction should collapse to the other operand");
        assertTrue(output.contains("print(true)"), "side effect assignment should keep simplified expression");
        ImOptimizer.localOptRounds = 1;
    }

    @Test
    public void test_string_concat_elimination() throws IOException {
        ImOptimizer.localOptRounds = 10;
        test().lines(
            "package test",
            "   native print(string msg)",
            "   string stored = \"\"",
            "   @extern native I2S(int i) returns string",
            "   function label(int i) returns string",
            "           stored = \"\" + I2S(i)",
            "           print(stored)",
            "           return stored",
            "   init",
            "           label(5)",
            "endpackage");

        String output = readOptimized("test_string_concat_elimination");
        assertFalse(output.contains("\"\" +"), "empty prefix should be removed from concatenation");
        assertTrue(output.contains("set test_stored = I2S(5)"), "side effect assignment should reference optimized call");
        ImOptimizer.localOptRounds = 1;
    }

    @Test
    public void test_string_hash_compare() throws IOException {
        boolean previousHashing = SimpleRewrites.doHashing;
        SimpleRewrites.doHashing = true;
        try {
            test().withStdLib().lines(
                "package test",
                "   function check(string s)",
                "           if s == \"wurst\"",
                "                   testSuccess()",
                "           else",
                "                   testFail(s)",
                "   init",
                "           check(\"wurst\")",
                "endpackage");

            String output = readOptimized("test_string_hash_compare");
            assertTrue(output.contains("StringHash"), "string comparisons should be rewritten to hash calls");
            assertFalse(output.contains("== \"wurst\""), "string literal should be replaced by its hash");
            assertTrue(output.contains("== -$55a37de1"));
        } finally {
            SimpleRewrites.doHashing = previousHashing;
        }
    }

    @Test
    public void test_consecutive_assignment_compaction() throws IOException {
        test().lines(
            "package test",
            "   native print(int msg)",
            "   int stored = 0",
            "   function bump(int x) returns int",
            "           int result = x * 2",
            "           result = result + 5",
            "           stored = result",
            "           print(stored)",
            "           return result",
            "   init",
            "           bump(4)",
            "endpackage");

        String output = readOptimized("test_consecutive_assignment_compaction");
        assertFalse(output.contains("set result = result + 5"), "second assignment should fold into the first");
        assertTrue(output.contains("set x =") && output.contains("+ 5"),
            "expected combined assignment with literal addition");
        assertTrue(output.contains("set test_stored = x"), "side effect assignment should remain after compaction");
    }

    @Test
    public void test_unreachable_return_removed() throws IOException {
        test().lines(
            "package test",
            "   native testSuccess()",
            "   native testFail(string s)",
            "   function choose() returns int",
            "           if true",
            "                   return 7",
            "           return 8",
            "   init",
            "           if choose() == 7",
            "                   testSuccess()",
            "           else",
            "                   testFail(\"unexpected\")",
            "endpackage");

        String output = readOptimized("test_unreachable_return_removed");
        assertFalse(output.contains("if true"), "trivial if should be eliminated");
        assertFalse(output.contains("return 8"), "unreachable code after constant return should be removed");
        assertFalse(output.contains("return 7"), "the entire func gets removed");
    }
}
