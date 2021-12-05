package de.peeeq.wurstscript.intermediatelang.optimizer;

public class PlayerHeroEvents {

    public static int getValForName(String name) {
        switch (name) {
            case "EVENT_PLAYER_HERO_LEVEL":
                return 41;
            case "EVENT_PLAYER_HERO_SKILL":
                return 42;
            case "EVENT_PLAYER_HERO_REVIVABLE":
                return 43;
            case "EVENT_PLAYER_HERO_REVIVE_START":
                return 44;
            case "EVENT_PLAYER_HERO_REVIVE_CANCEL":
                return 45;
            case "EVENT_PLAYER_HERO_REVIVE_FINISH":
                return 46;
        }
        return 0;
    }

}

