package de.peeeq.wurstscript.intermediatelang.optimizer;

public class PlayerUnitEvents {

    public static int getValForName(String name) {
        switch (name) {
            case "EVENT_PLAYER_UNIT_ATTACKED":
                return 18;
            case "EVENT_PLAYER_UNIT_RESCUED":
                return 19;
            case "EVENT_PLAYER_UNIT_DEATH":
                return 20;
            case "EVENT_PLAYER_UNIT_DECAY":
                return 21;
            case "EVENT_PLAYER_UNIT_DETECTED":
                return 22;
            case "EVENT_PLAYER_UNIT_HIDDEN":
                return 23;
            case "EVENT_PLAYER_UNIT_SELECTED":
                return 24;
            case "EVENT_PLAYER_UNIT_DESELECTED":
                return 25;
            case "EVENT_PLAYER_UNIT_CONSTRUCT_START":
                return 26;
            case "EVENT_PLAYER_UNIT_CONSTRUCT_CANCEL":
                return 27;
            case "EVENT_PLAYER_UNIT_CONSTRUCT_FINISH":
                return 28;
            case "EVENT_PLAYER_UNIT_UPGRADE_START":
                return 29;
            case "EVENT_PLAYER_UNIT_UPGRADE_CANCEL":
                return 30;
            case "EVENT_PLAYER_UNIT_UPGRADE_FINISH":
                return 31;
            case "EVENT_PLAYER_UNIT_TRAIN_START":
                return 32;
            case "EVENT_PLAYER_UNIT_TRAIN_CANCEL":
                return 33;
            case "EVENT_PLAYER_UNIT_TRAIN_FINISH":
                return 34;
            case "EVENT_PLAYER_UNIT_RESEARCH_START":
                return 35;
            case "EVENT_PLAYER_UNIT_RESEARCH_CANCEL":
                return 36;
            case "EVENT_PLAYER_UNIT_RESEARCH_FINISH":
                return 37;
            case "EVENT_PLAYER_UNIT_ISSUED_ORDER":
                return 38;
            case "EVENT_PLAYER_UNIT_ISSUED_POINT_ORDER":
                return 39;
            case "EVENT_PLAYER_UNIT_ISSUED_TARGET_ORDER":
                return 40;
            case "EVENT_PLAYER_UNIT_ISSUED_UNIT_ORDER":
                return 40;
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
            case "EVENT_PLAYER_UNIT_SUMMON":
                return 47;
            case "EVENT_PLAYER_UNIT_DROP_ITEM":
                return 48;
            case "EVENT_PLAYER_UNIT_PICKUP_ITEM":
                return 49;
            case "EVENT_PLAYER_UNIT_USE_ITEM":
                return 50;
            case "EVENT_PLAYER_UNIT_LOADED":
                return 51;
            case "EVENT_PLAYER_UNIT_DAMAGED":
                return 308;
            case "EVENT_PLAYER_UNIT_DAMAGING":
                return 315;
            case "EVENT_PLAYER_UNIT_SELL":
                return 269;
            case "EVENT_PLAYER_UNIT_CHANGE_OWNER":
                return 270;
            case "EVENT_PLAYER_UNIT_SELL_ITEM":
                return 271;
            case "EVENT_PLAYER_UNIT_SPELL_CHANNEL":
                return 272;
            case "EVENT_PLAYER_UNIT_SPELL_CAST":
                return 273;
            case "EVENT_PLAYER_UNIT_SPELL_EFFECT":
                return 274;
            case "EVENT_PLAYER_UNIT_SPELL_FINISH":
                return 275;
            case "EVENT_PLAYER_UNIT_SPELL_ENDCAST":
                return 276;
            case "EVENT_PLAYER_UNIT_PAWN_ITEM":
                return 277;
            case "EVENT_PLAYER_UNIT_STACK_ITEM":
                return 319;
        }
        return 0;
    }

}

