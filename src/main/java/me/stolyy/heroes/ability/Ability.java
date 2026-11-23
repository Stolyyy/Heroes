package me.stolyy.heroes.ability;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.util.Vector;

public interface Ability {
    String getName();
    String getDescription();
    AbilityType getType();
    long getCooldown(); // Configured duration in ms
    int getEnergyCost();
    Ammo getAmmo();
    
    // The stateful cooldown tracker for this instance
    Cooldown getCooldownObject();

    /**
     * The cooldown the hotbar slot should render. Variant families (one slot, several sub-abilities
     * with their own timers) override this to show whichever timer actually gates the cast the
     * player is currently aiming, so the item can't read "Ready" and then refuse.
     */
    default Cooldown getDisplayCooldown(Hero hero) { return getCooldownObject(); }

    /**
     * Every cooldown this ability owns, including sub-ability timers. Used by admin tooling
     * (/forceready) so a variant family isn't left half-locked.
     */
    default java.util.List<Cooldown> getAllCooldowns() { return java.util.List.of(getCooldownObject()); }
    
    // Returns true if ability was successfully used (triggering cooldown)
    boolean execute(Hero hero);
    boolean canExecute(Hero hero);

    // Initialization hook for true passives or setup
    default void initialize(Hero hero) {}

    // Callbacks for ability interactions
    default void onHit(Hero attacker, org.bukkit.entity.LivingEntity victim) {}
    default void onHitWall(Hero hero, org.bukkit.Location location) {}
    default void onExpire(Hero hero, org.bukkit.Location location) {}
    
    // Death cleanup. Default behavior lives in BaseAbility: resets cooldown if the ability
    // is currently in-use. Abilities that need additional cleanup should override and call
    // super.onDeath(hero) to preserve the cooldown reset.
    default void onDeath(Hero hero) {}
    default void onKill(Hero hero, org.bukkit.entity.LivingEntity victim) {}

    /**
     * A1 lifecycle: independent abilities have released state that acts on the world without
     * a live, playing hero — in-flight projectiles, planted objects, minion duration reapers.
     * When a hero is ELIMINATED it goes dormant: normal ticking stops, but abilities returning
     * true here keep receiving {@link #tick(Hero)} so their detached state resolves naturally.
     * Character-bound abilities (dashes, buffs, climbs) stay false — the safe default.
     */
    default boolean isIndependent() { return false; }

    /**
     * Whether this ability's effect can reach a target through solid geometry. Melee, dash,
     * and buff/passive abilities default true — a wall check only makes sense for something
     * that travels, and the bot AI's wall gate (BaseBotAI.tryUseAbility) is the only reader.
     * {@link me.stolyy.heroes.ability.template.RangedAbility} overrides this with its own
     * {@code pierces_walls} config field (default false).
     */
    default boolean piercesWalls() { return true; }

    /**
     * A1 lifecycle: fired ONLY when the hero reference is lost for good — logout or game-end
     * teardown (via {@link Hero#cleanup()}). Never fired on death, respawn, or elimination.
     * Override to expire detached world state (remove projectile displays, planted objects).
     */
    default void onOwnerLost(Hero hero) {}

    // Global Hooks
    default void onJabHit(Hero hero, org.bukkit.entity.LivingEntity victim) {}
    default void onAbilityHit(Hero hero, Ability ability, org.bukkit.entity.LivingEntity victim) {}
    default void onAbilityUse(Hero hero, Ability ability) {}
    default void onDamageTaken(Hero hero, org.bukkit.entity.Entity source, double amount) {}
    default void onKnockbackTaken(Hero hero, org.bukkit.entity.Entity source, Vector knockback) {}
    default void onLand(Hero hero) {}
    default void onDoubleJump(Hero hero) {}
    default void tick(Hero hero) {}
    /** SMASH cooldown (ms) removed when a hit from this ability lands; see templates.yml ability.ult_charge_ms. */
    default long ultChargeMs(Hero hero) { return hero.defaultAbilityUltChargeMs(); }

    /**
     * Overrides the text shown during the ability's active phase.
     * Return null to use the default time-remaining countdown (e.g. "7s").
     *
     * <p>For a SMASH slot this doubles as the activity signal: a non-null return keeps the ult HUD
     * open even when {@code isInUse()} is false, which is how charge-based ults with no fixed
     * {@code active_duration} (Bulk's multi-slam) stay on screen. Such an ability MUST return null
     * the moment it finishes, or the HUD never clears. Timed abilities return null throughout and
     * are driven purely by {@code isInUse()}.
     * Example: Bulk's ult returns "3 smashes left" instead of seconds.
     */
    default String getActiveStatusText() { return null; }

    // Dynamic Modification
    default void setDamage(double damage) {}
    default double getDamage() { return 0; }
    default void setKnockback(double knockback) {}
    default double getKnockback() { return 0; }
    default void setRadius(double radius) {}
    default double getRadius() { return 0; }
    default void setCooldown(long ms) {}
    default void setEnergyCost(int cost) {}
}
