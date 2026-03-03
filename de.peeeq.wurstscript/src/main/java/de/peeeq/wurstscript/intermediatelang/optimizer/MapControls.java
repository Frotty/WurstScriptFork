package de.peeeq.wurstscript.intermediatelang.optimizer;

public class MapControls {

    public static int getValForName(String name) {
        switch (name) {
            case "MAP_CONTROL_USER":
                return 0;
            case "MAP_CONTROL_COMPUTER":
                return 1;
            case "MAP_CONTROL_RESCUABLE":
                return 2;
            case "MAP_CONTROL_NEUTRAL":
                return 3;
            case "MAP_CONTROL_CREEP":
                return 4;
            case "MAP_CONTROL_NONE":
                return 5;
        }
        return 0;
    }
}

