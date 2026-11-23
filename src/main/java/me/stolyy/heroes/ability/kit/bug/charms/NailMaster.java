package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;

public class NailMaster extends Charm {
    private final double soulPerHit;

    public NailMaster(ConfigurationSection config) {
        this.soulPerHit = config != null ? config.getDouble("soul_per_hit", 17.0) : 17.0;
    }

    @Override
    protected void onActivate(Hero hero) {}

    @Override
    protected void onDeactivate(Hero hero) {}

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (ability.getType() != AbilityType.PRIMARY) return;
        hero.getEnergy().changeEnergy((float) soulPerHit);
    }
}
