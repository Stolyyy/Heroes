package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import org.bukkit.configuration.ConfigurationSection;

public class HeavyBlow extends Charm {
    private final double kbMult;

    public HeavyBlow(ConfigurationSection config) {
        this.kbMult = Configs.requireDouble(config, "kb_dealt_mult");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getCombat().setKnockbackMultiplier(kbMult);
        Ability dash = hero.getPrimaryAbility();
        dash.setKnockback(dash.getKnockback() * kbMult);
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getCombat().setKnockbackMultiplier(1.0);
        Ability dash = hero.getPrimaryAbility();
        dash.setKnockback(dash.getKnockback() / kbMult);
    }
}
