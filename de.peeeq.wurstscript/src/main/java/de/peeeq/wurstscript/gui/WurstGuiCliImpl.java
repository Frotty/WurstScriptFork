package de.peeeq.wurstscript.gui;

import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.attributes.CompileError;

/**
 * implementation for use with cli interfaces
 */
public class WurstGuiCliImpl extends WurstGui {


    @Override
    public void sendError(CompileError err) {
        super.sendError(err);
        WLogger.warning("Error: ", err);
    }

    @Override
    public void sendProgress(String msg) {
    }

    @Override
    public void sendFinished() {
        System.out.println("compilation finished (errors: " + getErrorCount() + ", warnings: " + getWarningList().size() + ")");
    }

    @Override
    public void showInfoMessage(String message) {
        System.out.println(message);

    }


}
