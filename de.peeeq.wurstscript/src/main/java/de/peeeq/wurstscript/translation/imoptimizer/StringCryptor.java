package de.peeeq.wurstscript.translation.imoptimizer;

import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.jassIm.ImProg;
import de.peeeq.wurstscript.jassIm.ImStringVal;
import de.peeeq.wurstscript.jassIm.JassIm;
import de.peeeq.wurstscript.translation.imtranslation.CallType;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;

public class StringCryptor {
    public static HashSet<String> forbidden = new HashSet<>();
    public static HashMap<String, Integer> charToVal = new HashMap<>();
    public static HashMap<Integer, String> valToChar = new HashMap<>();
    public static int[] key = new int[]{25, 12, 33, 17};

    static {
        forbidden.add("TRIGSTR");
        valToChar.put(32, " ");
        charToVal.put(" ", 32);
        valToChar.put(33, "!");
        charToVal.put("!", 33);
        valToChar.put(34, "\"");
        charToVal.put("\"", 34);
        valToChar.put(35, "#");
        charToVal.put("#", 35);
        valToChar.put(36, "$");
        charToVal.put("$", 36);
        valToChar.put(37, "%");
        charToVal.put("%", 37);
        valToChar.put(38, "&");
        charToVal.put("&", 38);
        valToChar.put(39, "'");
        charToVal.put("'", 39);
        valToChar.put(40, "(");
        charToVal.put("(", 40);
        valToChar.put(41, ")");
        charToVal.put(")", 41);
        valToChar.put(42, "*");
        charToVal.put("*", 42);
        valToChar.put(43, "+");
        charToVal.put("+", 43);
        valToChar.put(44, ",");
        charToVal.put(",", 44);
        valToChar.put(45, "-");
        charToVal.put("-", 45);
        valToChar.put(46, ".");
        charToVal.put(".", 46);
        valToChar.put(47, "/");
        charToVal.put("/", 47);
        valToChar.put(48, "0");
        charToVal.put("0", 48);
        valToChar.put(49, "1");
        charToVal.put("1", 49);
        valToChar.put(50, "2");
        charToVal.put("2", 50);
        valToChar.put(51, "3");
        charToVal.put("3", 51);
        valToChar.put(52, "4");
        charToVal.put("4", 52);
        valToChar.put(53, "5");
        charToVal.put("5", 53);
        valToChar.put(54, "6");
        charToVal.put("6", 54);
        valToChar.put(55, "7");
        charToVal.put("7", 55);
        valToChar.put(56, "8");
        charToVal.put("8", 56);
        valToChar.put(57, "9");
        charToVal.put("9", 57);
        valToChar.put(58, ":");
        charToVal.put(":", 58);
        valToChar.put(59, ";");
        charToVal.put(";", 59);
        valToChar.put(60, "<");
        charToVal.put("<", 60);
        valToChar.put(61, "=");
        charToVal.put("=", 61);
        valToChar.put(62, ">");
        charToVal.put(">", 62);
        valToChar.put(63, "?");
        charToVal.put("?", 63);
        valToChar.put(64, "@");
        charToVal.put("@", 64);
        valToChar.put(65, "A");
        charToVal.put("A", 65);
        valToChar.put(66, "B");
        charToVal.put("B", 66);
        valToChar.put(67, "C");
        charToVal.put("C", 67);
        valToChar.put(68, "D");
        charToVal.put("D", 68);
        valToChar.put(69, "E");
        charToVal.put("E", 69);
        valToChar.put(70, "F");
        charToVal.put("F", 70);
        valToChar.put(71, "G");
        charToVal.put("G", 71);
        valToChar.put(72, "H");
        charToVal.put("H", 72);
        valToChar.put(73, "I");
        charToVal.put("I", 73);
        valToChar.put(74, "J");
        charToVal.put("J", 74);
        valToChar.put(75, "K");
        charToVal.put("K", 75);
        valToChar.put(76, "L");
        charToVal.put("L", 76);
        valToChar.put(77, "M");
        charToVal.put("M", 77);
        valToChar.put(78, "N");
        charToVal.put("N", 78);
        valToChar.put(79, "O");
        charToVal.put("O", 79);
        valToChar.put(80, "P");
        charToVal.put("P", 80);
        valToChar.put(81, "Q");
        charToVal.put("Q", 81);
        valToChar.put(82, "R");
        charToVal.put("R", 82);
        valToChar.put(83, "S");
        charToVal.put("S", 83);
        valToChar.put(84, "T");
        charToVal.put("T", 84);
        valToChar.put(85, "U");
        charToVal.put("U", 85);
        valToChar.put(86, "V");
        charToVal.put("V", 86);
        valToChar.put(87, "W");
        charToVal.put("W", 87);
        valToChar.put(88, "X");
        charToVal.put("X", 88);
        valToChar.put(89, "Y");
        charToVal.put("Y", 89);
        valToChar.put(90, "Z");
        charToVal.put("Z", 90);
        valToChar.put(91, "[");
        charToVal.put("[", 91);
        valToChar.put(92, "\\");
        charToVal.put("\\", 92);
        valToChar.put(93, "]");
        charToVal.put("]", 93);
        valToChar.put(94, "^");
        charToVal.put("^", 94);
        valToChar.put(95, "_");
        charToVal.put("_", 95);
        valToChar.put(96, "`");
        charToVal.put("`", 96);
        valToChar.put(97, "a");
        charToVal.put("a", 97);
        valToChar.put(98, "b");
        charToVal.put("b", 98);
        valToChar.put(99, "c");
        charToVal.put("c", 99);
        valToChar.put(100, "d");
        charToVal.put("d", 100);
        valToChar.put(101, "e");
        charToVal.put("e", 101);
        valToChar.put(102, "f");
        charToVal.put("f", 102);
        valToChar.put(103, "g");
        charToVal.put("g", 103);
        valToChar.put(104, "h");
        charToVal.put("h", 104);
        valToChar.put(105, "i");
        charToVal.put("i", 105);
        valToChar.put(106, "j");
        charToVal.put("j", 106);
        valToChar.put(107, "k");
        charToVal.put("k", 107);
        valToChar.put(108, "l");
        charToVal.put("l", 108);
        valToChar.put(109, "m");
        charToVal.put("m", 109);
        valToChar.put(110, "n");
        charToVal.put("n", 110);
        valToChar.put(111, "o");
        charToVal.put("o", 111);
        valToChar.put(112, "p");
        charToVal.put("p", 112);
        valToChar.put(113, "q");
        charToVal.put("q", 113);
        valToChar.put(114, "r");
        charToVal.put("r", 114);
        valToChar.put(115, "s");
        charToVal.put("s", 115);
        valToChar.put(116, "t");
        charToVal.put("t", 116);
        valToChar.put(117, "u");
        charToVal.put("u", 117);
        valToChar.put(118, "v");
        charToVal.put("v", 118);
        valToChar.put(119, "w");
        charToVal.put("w", 119);
        valToChar.put(120, "x");
        charToVal.put("x", 120);
        valToChar.put(121, "y");
        charToVal.put("y", 121);
        valToChar.put(122, "z");
        charToVal.put("z", 122);
        valToChar.put(123, "{");
        charToVal.put("{", 123);
        valToChar.put(124, "|");
        charToVal.put("|", 124);
        valToChar.put(125, "}");
        charToVal.put("}", 125);
        valToChar.put(126, "~");
        charToVal.put("~", 126);
    }

    public static void encrypt(ImTranslator trans) {
        ImProg prog = trans.getImProg();
        Optional<ImFunction> decryptFunc = prog.getFunctions().stream().filter(f -> f.getName().contains("w3pro_decrypt_string")).findFirst();
        if (decryptFunc.isPresent()) {
            ImFunction descryptFunction = decryptFunc.get();
            prog.accept(new ImProg.DefaultVisitor() {
                @Override
                public void visit(ImStringVal stringVal) {
                    Optional<String> forbidden = StringCryptor.forbidden.stream().filter(f -> stringVal.getValS().startsWith(f)).findFirst();
                    if (!forbidden.isPresent()) {
                        if (stringVal.getValS().startsWith("w3pro_")) {
                            stringVal.replaceBy(JassIm.ImStringVal(stringVal.getValS().substring(6)));
                        } else {
                            String text = stringVal.getValS();
                            StringBuilder crypted = new StringBuilder();
                            int i = 0;
                            for (char c : text.toCharArray()) {
                                if (charToVal.containsKey("" + c)) {
                                    int shifted = (charToVal.get("" + c) - (key[i]));
                                    if (shifted < 32) {
                                        shifted += (127 - 32);
                                    }
                                    crypted.append(valToChar.get(shifted));
                                } else {
                                    crypted.append(c);
                                }
                                i++;
                                if (i >= 4) {
                                    i = 0;
                                }
                            }
                            stringVal.replaceBy(JassIm.ImFunctionCall(descryptFunction.attrTrace(), descryptFunction,
                                JassIm.ImTypeArguments(), JassIm.ImExprs(JassIm.ImStringVal(crypted.toString())), true, CallType.NORMAL));
                        }
                    }

                }
            });
        }
    }


}
