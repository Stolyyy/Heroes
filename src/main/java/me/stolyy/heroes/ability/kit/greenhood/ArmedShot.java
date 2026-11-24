package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.hero.Hero;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.util.Vector;

interface ArmedShot extends Ability {
    boolean isArmed();

    void disarm();

    static void disarmOthers(Hero hero, ArmedShot arming) {
        for (Ability ability : hero.getAbilities()) {
            if (ability instanceof ArmedShot other && other != arming) {
                other.disarm();
            }
        }
    }

    boolean fireFromBow(Hero hero, GreenHoodBow bow, AbstractArrow arrow, Vector aim, Vector carried,
                        double charge, int overchargeTicks, double force);
}
