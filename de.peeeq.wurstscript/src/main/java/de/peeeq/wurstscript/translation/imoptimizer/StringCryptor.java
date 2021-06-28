package de.peeeq.wurstscript.translation.imoptimizer;

import com.google.common.collect.Sets;
import de.peeeq.wurstscript.ast.Element;
import de.peeeq.wurstscript.attributes.CompileError;
import de.peeeq.wurstscript.jassIm.*;
import de.peeeq.wurstscript.parser.WPos;
import de.peeeq.wurstscript.translation.imtranslation.CallType;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;
import de.peeeq.wurstscript.utils.Utils;
import de.peeeq.wurstscript.validation.TRVEHelper;
import org.apache.commons.lang.math.RandomUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class StringCryptor {
    public static HashSet<String> forbidden = new HashSet<>();
    static {
        forbidden.add("TRIGSTR");
    }
    public static void encrypt(ImTranslator trans) {
        ImProg prog = trans.getImProg();
        int[] key = new int[]{25, 12, 33, 17};
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
                                if (c > 31 && c < 127) {
                                    char shifted = (char)(c - ((char) key[i]));
                                    if (shifted < 32) {
                                        shifted += (128 - 32);
                                    }
                                    crypted.append(shifted);
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
