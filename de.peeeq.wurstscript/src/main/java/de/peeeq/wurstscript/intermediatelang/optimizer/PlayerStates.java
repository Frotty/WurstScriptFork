package de.peeeq.wurstscript.intermediatelang.optimizer;

public class PlayerStates {

    public static int getValForName(String name) {
        switch (name) {
            case "PLAYER_STATE_GAME_RESULT":
                return 0;
            case "PLAYER_STATE_RESOURCE_GOLD":
                return 1;
            case "PLAYER_STATE_RESOURCE_LUMBER":
                return 2;
            case "PLAYER_STATE_RESOURCE_HERO_TOKENS":
                return 3;
            case "PLAYER_STATE_RESOURCE_FOOD_CAP":
                return 4;
            case "PLAYER_STATE_RESOURCE_FOOD_USED":
                return 5;
            case "PLAYER_STATE_FOOD_CAP_CEILING":
                return 6;
            case "PLAYER_STATE_GIVES_BOUNTY":
                return 7;
            case "PLAYER_STATE_ALLIED_VICTORY":
                return 8;
            case "PLAYER_STATE_PLACED":
                return 9;
            case "PLAYER_STATE_OBSERVER_ON_DEATH":
                return 10;
            case "PLAYER_STATE_OBSERVER":
                return 11;
            case "PLAYER_STATE_UNFOLLOWABLE":
                return 12;
            case "PLAYER_STATE_GOLD_UPKEEP_RATE":
                return 13;
            case "PLAYER_STATE_LUMBER_UPKEEP_RATE":
                return 14;
            case "PLAYER_STATE_GOLD_GATHERED":
                return 15;
            case "PLAYER_STATE_LUMBER_GATHERED":
                return 16;
            case "PLAYER_STATE_NO_CREEP_SLEEP":
                return 25;
        }
        return 0;
    }

}

