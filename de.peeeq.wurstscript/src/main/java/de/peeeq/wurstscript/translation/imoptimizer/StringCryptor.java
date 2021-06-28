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
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class StringCryptor {

    public void encrypt(ImTranslator trans) {
        ImProg prog = trans.getImProg();
        Optional<ImFunction> decryptFunc = prog.getFunctions().stream().filter(f -> f.getName().contains("w3pro_decrypt_string")).findFirst();
        if (decryptFunc.isPresent()) {
            ImFunction descryptFunction = decryptFunc.get();
            prog.accept(new ImProg.DefaultVisitor() {
                @Override
                public void visit(ImStringVal stringVal) {
                    Element trace = stringVal.attrTrace();
                    WPos wPos = trace.attrSource();
                    if (!stringVal.attrTrace().attrSource().getFile().equals(descryptFunction.attrTrace().attrSource().getFile())) {
                        stringVal.replaceBy(JassIm.ImFunctionCall(descryptFunction.attrTrace(), descryptFunction, JassIm.ImTypeArguments(), JassIm.ImExprs(stringVal.copy()), true, CallType.NORMAL));
                    }
                }
            });
        }
    }


}
