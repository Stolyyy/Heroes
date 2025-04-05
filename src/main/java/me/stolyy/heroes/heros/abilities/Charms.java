package me.stolyy.heroes.heros.abilities;

import java.util.EnumMap;
import java.util.Map;

public enum Charms {
    DASHMASTER(2), //less dash cd
    GRUBSONG(1), //soul when hit
    HEAVY_BLOW(1), //more kb on dash/spells
    KINGSOUL(4), //auto soul regen
    MARK_OF_PRIDE(3), //more jab range
    NAIL_MASTER(1),
    QUICK_SLASH(3), //quicker jabs
    SHAMAN_STONE(3), //more spell dmg
    SHARP_SHADOW(2), //more dash dmg
    SOUL_EATER(3), //most souls per hit
    SOUL_TWISTER(2), //less cost per spells
    SPRINTMASTER(1), //faster move speed
    STEADY_BODY(3), //weight
    STRENGTH(3), //jab dmg
    WEAVERSONG(2); //spider minions

    final int cost;

    Charms(int cost) {
        this.cost = cost;
    }
}
