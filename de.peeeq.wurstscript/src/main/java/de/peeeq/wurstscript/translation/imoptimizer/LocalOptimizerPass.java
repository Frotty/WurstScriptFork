package de.peeeq.wurstscript.translation.imoptimizer;

import de.peeeq.wurstscript.jassIm.ImFunction;
import de.peeeq.wurstscript.translation.imtranslation.ImHelper;
import de.peeeq.wurstscript.translation.imtranslation.ImTranslator;

public interface LocalOptimizerPass extends OptimizerPass {

    default void beginRound(ImTranslator trans) {
    }

    int optimizeFunction(ImFunction func, ImTranslator trans);

    default void endRound(ImTranslator trans) {
    }

    default boolean shouldOptimize(ImFunction func) {
        return !func.isNative() && !func.isBj();
    }

    @Override
    default int optimize(ImTranslator trans) {
        beginRound(trans);
        int total = 0;
        for (ImFunction func : ImHelper.calculateFunctionsOfProg(trans.getImProg())) {
            if (!shouldOptimize(func)) {
                continue;
            }
            total += optimizeFunction(func, trans);
        }
        endRound(trans);
        return total;
    }
}
