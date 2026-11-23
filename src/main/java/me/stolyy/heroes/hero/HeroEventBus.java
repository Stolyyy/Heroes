package me.stolyy.heroes.hero;

import me.stolyy.heroes.ability.Ability;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class HeroEventBus {

    // Copy-on-write throughout: dispatch runs 20Hz per hero and listeners routinely register or
    // tear down other listeners mid-dispatch (abilities re-registering on swap, minions expiring),
    // which was throwing ConcurrentModificationException out of the middle of a tick. Registration
    // is rare enough that the copy cost is irrelevant.
    private final List<Consumer<Ability>> onAbilityUseListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<LivingEntity>> onJabHitListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<Ability, LivingEntity>> onAbilityHitListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Hero>> onTickListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Void>> onLandListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Void>> onDoubleJumpListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Location>> onDeathListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<LivingEntity>> onKillListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<org.bukkit.entity.Entity, Double>> onDamageTakenListeners = new CopyOnWriteArrayList<>();
    private final List<BiConsumer<org.bukkit.entity.Entity, Vector>> onKnockbackTakenListeners = new CopyOnWriteArrayList<>();

    // --- Registration Methods ---

    public void onAbilityUse(Consumer<Ability> callback) { onAbilityUseListeners.add(callback); }
    public void onJabHit(Consumer<LivingEntity> callback) { onJabHitListeners.add(callback); }
    public void onAbilityHit(BiConsumer<Ability, LivingEntity> callback) { onAbilityHitListeners.add(callback); }
    public void onTick(Consumer<Hero> callback) { onTickListeners.add(callback); }
    public void onLand(Consumer<Void> callback) { onLandListeners.add(callback); }
    public void onDoubleJump(Consumer<Void> callback) { onDoubleJumpListeners.add(callback); }
    /** The callback receives the location the hero died at, which may be null when unknown. */
    public void onDeath(Consumer<Location> callback) { onDeathListeners.add(callback); }
    public void onKill(Consumer<LivingEntity> callback) { onKillListeners.add(callback); }
    public void onDamageTaken(BiConsumer<org.bukkit.entity.Entity, Double> callback) { onDamageTakenListeners.add(callback); }
    public void onKnockbackTaken(BiConsumer<org.bukkit.entity.Entity, Vector> callback) { onKnockbackTakenListeners.add(callback); }

    // --- Trigger Methods ---

    public void triggerAbilityUse(Ability ability) {
        onAbilityUseListeners.forEach(c -> c.accept(ability));
    }

    public void triggerJabHit(LivingEntity victim) {
        onJabHitListeners.forEach(c -> c.accept(victim));
    }

    public void triggerAbilityHit(Ability ability, LivingEntity victim) {
        onAbilityHitListeners.forEach(c -> c.accept(ability, victim));
    }

    public void triggerDamageTaken(org.bukkit.entity.Entity source, double amount) {
        onDamageTakenListeners.forEach(c -> c.accept(source, amount));
    }

    public void triggerKnockbackTaken(org.bukkit.entity.Entity source, Vector knockback) {
        onKnockbackTakenListeners.forEach(c -> c.accept(source, knockback));
    }

    public void triggerLand() {
        onLandListeners.forEach(c -> c.accept(null));
    }

    public void triggerDoubleJump() {
        onDoubleJumpListeners.forEach(c -> c.accept(null));
    }

    /**
     * The death location is passed explicitly rather than read from the controller, because by
     * the time this fires the entity has already been moved to its respawn or spectator point.
     */
    public void triggerDeath(Location deathLocation) {
        onDeathListeners.forEach(c -> c.accept(deathLocation));
    }

    public void triggerKill(LivingEntity victim) {
        onKillListeners.forEach(c -> c.accept(victim));
    }

    public void triggerTick(Hero hero) {
        onTickListeners.forEach(c -> c.accept(hero));
    }

    // --- Lifecycle ---

    /**
     * A1 lifecycle contract: clear() may only run on reference-lost / game-end teardown
     * (i.e. from {@link Hero#cleanup()}), AFTER the ability teardown hooks have fired.
     * Death, respawn, and ELIMINATION must never clear the bus — an eliminated hero's
     * independent abilities (in-flight projectiles) keep using it for hit/kill triggers.
     */
    public void clear() {
        onAbilityUseListeners.clear();
        onJabHitListeners.clear();
        onAbilityHitListeners.clear();
        onTickListeners.clear();
        onLandListeners.clear();
        onDoubleJumpListeners.clear();
        onDeathListeners.clear();
        onKillListeners.clear();
        onDamageTakenListeners.clear();
        onKnockbackTakenListeners.clear();
    }
}
