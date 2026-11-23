package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.util.Configs;
import org.bukkit.configuration.ConfigurationSection;

public class SharpShadow extends Charm {
    private final double damageMult;
    private final double speedMult;

    public SharpShadow(ConfigurationSection config) {
        this.damageMult = Configs.requireDouble(config, "damage_mult");
        this.speedMult = Configs.requireDouble(config, "speed_mult");
    }

    @Override
    protected void onActivate(Hero hero) {
        Ability primary = hero.getPrimaryAbility();
        if (primary != null) {
            primary.setDamage(primary.getDamage() * damageMult);
        }

        if (primary instanceof Dash dash) {
            dash.setSpeed(dash.getSpeed() * speedMult);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        Ability primary = hero.getPrimaryAbility();
        if (primary != null) {
            primary.setDamage(primary.getDamage() / damageMult);
        }
        if (primary instanceof Dash dash) {
            dash.setSpeed(dash.getSpeed() / speedMult);
        }
    }
}
