package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base template for outer abilities that compose one or more inner {@link BaseAbility} instances
 * (mode families, projectile children, secondary shockwaves) and must forward tick, dormancy,
 * death, owner-loss, and cooldown inspection to all of them.
 */
public abstract class CompositeAbility extends BaseAbility {
    private final List<BaseAbility> children = new ArrayList<>();

    protected CompositeAbility(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    protected final <T extends BaseAbility> T addChild(T child) {
        children.add(child);
        return child;
    }

    protected final List<BaseAbility> children() {
        return Collections.unmodifiableList(children);
    }

    @Override
    protected final void onTick(Hero hero) {
        boolean dormant = hero.isEliminated();
        for (BaseAbility child : children) {
            if (!dormant || child.isIndependent()) {
                child.tick(hero);
            }
        }
        onCompositeTick(hero);
    }

    @Override
    public boolean isIndependent() {
        for (BaseAbility child : children) {
            if (child.isIndependent()) return true;
        }
        return false;
    }

    @Override
    public List<Cooldown> getAllCooldowns() {
        List<Cooldown> all = new ArrayList<>();
        all.add(getCooldownObject());
        for (BaseAbility child : children) {
            all.addAll(child.getAllCooldowns());
        }
        return all;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        for (BaseAbility child : children) {
            child.onDeath(hero);
        }
        onCompositeDeath(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        for (BaseAbility child : children) {
            child.onOwnerLost(hero);
        }
        onCompositeOwnerLost(hero);
    }

    @Override
    public void addDamageMultiplier(Object key, double mult) {
        super.addDamageMultiplier(key, mult);
        for (BaseAbility child : children) child.addDamageMultiplier(key, mult);
    }

    @Override
    public void addDamageFlat(Object key, double delta) {
        super.addDamageFlat(key, delta);
        for (BaseAbility child : children) child.addDamageFlat(key, delta);
    }

    @Override
    public void removeDamageModifier(Object key) {
        super.removeDamageModifier(key);
        for (BaseAbility child : children) child.removeDamageModifier(key);
    }

    @Override
    public void addKnockbackMultiplier(Object key, double mult) {
        super.addKnockbackMultiplier(key, mult);
        for (BaseAbility child : children) child.addKnockbackMultiplier(key, mult);
    }

    @Override
    public void addKnockbackFlat(Object key, double delta) {
        super.addKnockbackFlat(key, delta);
        for (BaseAbility child : children) child.addKnockbackFlat(key, delta);
    }

    @Override
    public void removeKnockbackModifier(Object key) {
        super.removeKnockbackModifier(key);
        for (BaseAbility child : children) child.removeKnockbackModifier(key);
    }

    @Override
    public void addRadiusMultiplier(Object key, double mult) {
        super.addRadiusMultiplier(key, mult);
        for (BaseAbility child : children) child.addRadiusMultiplier(key, mult);
    }

    @Override
    public void addRadiusFlat(Object key, double delta) {
        super.addRadiusFlat(key, delta);
        for (BaseAbility child : children) child.addRadiusFlat(key, delta);
    }

    @Override
    public void removeRadiusModifier(Object key) {
        super.removeRadiusModifier(key);
        for (BaseAbility child : children) child.removeRadiusModifier(key);
    }

    protected void onCompositeTick(Hero hero) {}
    protected void onCompositeDeath(Hero hero) {}
    protected void onCompositeOwnerLost(Hero hero) {}
}
