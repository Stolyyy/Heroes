package me.stolyy.heroes.ability.kit.bug.charms;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

/**
 * Base class for individual Bug charms. Each charm is constructed with its own
 * configuration section, and lifecycle is driven by {@link #activate(Hero)} /
 * {@link #deactivate(Hero)} from {@link me.stolyy.heroes.ability.kit.bug.BugCharms}.
 *
 * <p>Charms that modify state (attributes, ability properties, etc.) implement
 * {@link #onActivate(Hero)} and {@link #onDeactivate(Hero)} to apply and revert.
 * Event-driven charms (e.g. Soul Eater, Grubsong, Nail Master) override the
 * relevant event hook and may leave activate/deactivate as no-ops.
 *
 * <p>{@code activate} and {@code deactivate} are {@code final} and idempotency-guarded —
 * subclasses don't have to think about double-activation.
 */
public abstract class Charm {
    protected boolean active = false;

    public final void activate(Hero hero) {
        if (active) return;
        active = true;
        onActivate(hero);
    }

    public final void deactivate(Hero hero) {
        if (!active) return;
        active = false;
        onDeactivate(hero);
    }

    public boolean isActive() {
        return active;
    }

    protected abstract void onActivate(Hero hero);
    protected abstract void onDeactivate(Hero hero);

    public void onJabHit(Hero hero, LivingEntity victim) {}
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {}
    public void onDamageTaken(Hero hero, Entity source, double amount) {}
    public void tick(Hero hero) {}
}
