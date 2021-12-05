package de.peeeq.wurstscript.intermediatelang.optimizer;

public class PlayerEvents {

    public static int getValForName(String name) {
        switch (name) {
            case "EVENT_PLAYER_STATE_LIMIT":
                return 11;
            case "EVENT_PLAYER_ALLIANCE_CHANGED":
                return 12;
            case "EVENT_PLAYER_DEFEAT":
                return 13;
            case "EVENT_PLAYER_VICTORY":
                return 14;
            case "EVENT_PLAYER_LEAVE":
                return 15;
            case "EVENT_PLAYER_CHAT":
                return 16;
            case "EVENT_PLAYER_END_CINEMATIC":
                return 17;
            case "EVENT_PLAYER_ARROW_LEFT_DOWN":
                return 261;
            case "EVENT_PLAYER_ARROW_LEFT_UP":
                return 262;
            case "EVENT_PLAYER_ARROW_RIGHT_DOWN":
                return 263;
            case "EVENT_PLAYER_ARROW_RIGHT_UP":
                return 264;
            case "EVENT_PLAYER_ARROW_DOWN_DOWN":
                return 265;
            case "EVENT_PLAYER_ARROW_DOWN_UP":
                return 266;
            case "EVENT_PLAYER_ARROW_UP_DOWN":
                return 267;
            case "EVENT_PLAYER_ARROW_UP_UP":
                return 268;
            case "EVENT_PLAYER_MOUSE_DOWN":
                return 305;
            case "EVENT_PLAYER_MOUSE_UP":
                return 306;
            case "EVENT_PLAYER_MOUSE_MOVE":
                return 307;
            case "EVENT_PLAYER_SYNC_DATA":
                return 309;
            case "EVENT_PLAYER_KEY":
                return 311;
            case "EVENT_PLAYER_KEY_DOWN":
                return 312;
            case "EVENT_PLAYER_KEY_UP":
                return 313;
        }
        return 0;
    }

}

