package de.peeeq.wurstscript.intermediatelang.optimizer;

public class PlayerSlotStates {

    public static int getValForName(String name) {
        switch (name) {
            case "PLAYER_SLOT_STATE_EMPTY":
                return 0;
            case "PLAYER_SLOT_STATE_PLAYING":
                return 1;
            case "PLAYER_SLOT_STATE_LEFT":
                return 2;
        }
        return 0;
    }
}

