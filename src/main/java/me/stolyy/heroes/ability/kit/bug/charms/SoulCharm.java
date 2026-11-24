package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.hero.Hero;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Data-driven Bug charm that modifies soul/energy gain on combat events.
 */
public class SoulCharm extends Charm {
    public enum Trigger { JAB_MULTIPLIER, ON_DAMAGE_TAKEN, ON_PRIMARY_HIT }

    private final Trigger trigger;
    private final double value;

    public SoulCharm(Trigger trigger, double value) {
        this.trigger = trigger;
        this.value = value;
    }

    @Override
    protected void onActivate(Hero hero) {}

    @Override
    protected void onDeactivate(Hero hero) {}

    @Override
    public double soulMultiplier() {
        return trigger == Trigger.JAB_MULTIPLIER ? value : 1.0;
    }

    @Override
    public void onDamageTaken(Hero hero, Entity source, double amount) {
        if (trigger == Trigger.ON_DAMAGE_TAKEN) {
            hero.getEnergy().changeEnergy((float) (amount * value));
        }
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (trigger == Trigger.ON_PRIMARY_HIT && ability.getType() == AbilityType.PRIMARY) {
            hero.getEnergy().changeEnergy((float) value);
        }
    }
}
