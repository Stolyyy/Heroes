package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.function.Consumer;

public class Jab {
    private double cooldown; // in milliseconds
    private double damage;
    private double range;
    // Multiplicative modifiers (charms). Kept separate from the base stats so stat
    // re-application (Hero.resetStats -> apply) doesn't wipe an active charm's bonus.
    private double rangeMultiplier = 1.0;
    private double damageMultiplier = 1.0;
    private double cooldownMultiplier = 1.0;
    private double knockbackMultiplier = 1.0;
    private double radius;   // half-extent of the jab box cross-section (0.5 = 1x1 profile)
    private boolean pierce;  // false = closest target only, true = everyone in the box
    private double knockback;
    // Only a LANDED jab starts the cooldown. A swing that catches nobody costs nothing, so
    // stepping into range connects on the next click instead of being eaten by a swing thrown a
    // moment too early. This cannot raise DPS: every hit starts the full cooldown, so hits stay
    // a cooldown apart no matter how much you swing in between.
    //
    // The cooldown lives on the TICK GRID, not on wall-clock time. Packets are drained once per
    // tick, so currentTimeMillis() readings are already quantised to the 50 ms grid and a 500 ms
    // cooldown landed exactly on tick 10 — whether a click on the boundary counted was decided
    // by tick jitter rather than by the player.
    private int readyAtTick = Integer.MIN_VALUE;
    // The miss sound is the thing that actually needed throttling: without a gate, spam-clicking
    // machine-guns it. Held separately from the jab cooldown so it can never suppress a hit.
    private int swingSoundReadyAtTick = Integer.MIN_VALUE;
    // One left click sends an ATTACK packet and a SWING packet, both drained in the same tick.
    // The attack path is the accurate one and arrives first; this stops the swing that follows
    // it from spending a second jab on the same click.
    private int lastAttemptTick = Integer.MIN_VALUE;
    // Tick a jab last CONNECTED, so the two paths of one click can never both land.
    private int lastLandedTick = Integer.MIN_VALUE;

    private SoundEffect swingSound;
    private SoundEffect hitSound;

    private Consumer<LivingEntity> onHit;

    public Jab(HeroStats.CombatStats stats) {
        apply(stats, null);
    }

    public void apply(HeroStats.CombatStats stats) {
        apply(stats, null);
    }

    /**
     * Applies combat stats to this Jab instance. If {@code controller} is non-null, the Bukkit
     * entity's {@code ENTITY_INTERACTION_RANGE} attribute is synced to the new range so server-side
     * reach matches the jab's hit-detection range.
     */
    public void apply(HeroStats.CombatStats stats, Controller controller) {
        this.cooldown = stats.jabCooldown();
        this.damage = stats.jabDamage();
        this.range = stats.jabRange();
        this.radius = stats.jabRadius();
        this.pierce = stats.jabPierce();
        this.knockback = stats.jabKnockback();
        this.swingSound = stats.swingSound();
        this.hitSound = stats.hitSound();
        syncRangeAttribute(controller);
    }

    private void syncRangeAttribute(Controller controller) {
        if (controller == null) return;
        LivingEntity entity = controller.getEntity();
        if (entity == null) return;
        AttributeInstance attr = entity.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) attr.setBaseValue(getRange());
    }

    public void onHit(Consumer<LivingEntity> callback) {
        this.onHit = callback;
    }

    /**
     * Client-authoritative jab against a victim the attacker's own client picked. The client
     * raytraced that target against what it was actually drawing, so this path carries none of
     * the server-side position skew that a re-derived hit test does — the server's copy of a
     * moving player sits several ticks ahead of the one on screen. This is the path every player
     * attack that resolved a target takes; bots supply their target directly.
     */
    public boolean performJab(Controller attacker, LivingEntity victim) {
        if (!claimAttempt(true)) return false;

        applyJab(attacker, victim);

        Player player = (attacker.getEntity() instanceof Player p) ? p : null;
        if (hitSound != null) hitSound.play(attacker.getLocation(), player);
        startCooldown();
        return true;
    }

    /**
     * Fallback jab for a swing where the client resolved no target: sweeps a range x radius*2 x
     * radius*2 box along the attacker's look direction and hits the closest intersecting enemy
     * (all of them with {@code jab_pierce}). Walls block the sweep. A swing that catches nobody
     * plays the swing sound and costs no cooldown.
     */
    public boolean attemptJab(Controller attacker) {
        if (!claimAttempt(false)) return false;

        LivingEntity self = attacker.getEntity();
        Set<LivingEntity> victims = Hitbox.box(
                        attacker.getEyeLocation(), attacker.getEyeLocation().getDirection(),
                        radius * 2, radius * 2, getRange())
                .exclude(self)
                .excludeTeammates(self)
                .checkWalls(true)
                .pierce(pierce)
                .get();

        Player player = (self instanceof Player p) ? p : null;
        if (victims.isEmpty()) {
            playSwingSound(attacker, player);
            return false;
        }
        for (LivingEntity victim : victims) applyJab(attacker, victim);
        if (hitSound != null) hitSound.play(attacker.getLocation(), player);
        startCooldown();
        return true;
    }

    /**
     * Reserves this tick for one jab. Rejects a jab still on cooldown, and the second of the two
     * events a single click fires.
     *
     * <p>{@code authoritative} marks the client-target path. It is allowed to run even if the
     * fallback swing already went this tick, as long as that swing caught nothing: the client is
     * not contractually required to send its attack packet before its swing packet, and without
     * this a flipped order would let a fallback whiff silently suppress the accurate hit. Once
     * anything has LANDED this tick, both paths are done.
     */
    private boolean claimAttempt(boolean authoritative) {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        if (tick < readyAtTick) return false;
        if (tick == lastLandedTick) return false;
        if (tick == lastAttemptTick && !authoritative) return false;
        lastAttemptTick = tick;
        return true;
    }

    private void startCooldown() {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        lastLandedTick = tick;
        readyAtTick = tick + (int) Math.round(getCooldown() / 50.0);
    }

    /** Plays the miss sound at most once per cooldown, so spam-clicking does not machine-gun it. */
    private void playSwingSound(Controller attacker, Player player) {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        if (tick < swingSoundReadyAtTick) return;
        swingSoundReadyAtTick = tick + (int) Math.round(getCooldown() / 50.0);
        if (swingSound != null) swingSound.play(attacker.getLocation(), player);
    }

    private void applyJab(Controller attacker, LivingEntity victim) {
        // Use our interaction utility for standardized KB/Damage (jab KB is static, not
        // health-scaled). The ability name is what PlayerStats files this damage and any resulting
        // kill under. A minion jabs under its OWN name: the damage still credits the OWNER
        // (deliberate — see the comment in Interactions.applyDamage), this only gives the summon
        // its own stats row instead of burying its output in the owner's "Jab" line.
        Hero actor = HeroManager.getHero(attacker.getUniqueId());
        String jabName = actor instanceof MinionHero ? actor.getName() : "Jab";
        Interactions.on(attacker.getEntity(), victim)
                .damage(getDamage()).knockback(knockback * knockbackMultiplier).staticKnockback()
                .abilityName(jabName)
                .execute();

        if (onHit != null) onHit.accept(victim);
    }

    /** Effective jab cooldown: base cooldown from stats times any active multiplier. */
    public double getCooldown() { return cooldown * cooldownMultiplier; }
    public void setCooldown(double cooldown) { this.cooldown = cooldown; }
    public void setCooldownMultiplier(double multiplier) { this.cooldownMultiplier = multiplier; }
    /** Effective jab damage: base damage from stats times any active multiplier. */
    public double getDamage() { return damage * damageMultiplier; }
    public void setDamage(double damage) { this.damage = damage; }
    public void setDamageMultiplier(double multiplier) { this.damageMultiplier = multiplier; }
    public void setKnockbackMultiplier(double multiplier) { this.knockbackMultiplier = multiplier; }
    /** Effective jab range: base range from stats times any active multiplier. */
    public double getRange() { return range * rangeMultiplier; }

    /** Sets the base jab range without syncing the Bukkit interaction range attribute. */
    public void setRange(double range) { this.range = range; }

    /**
     * Sets the base jab range AND syncs the Bukkit {@code ENTITY_INTERACTION_RANGE} attribute
     * on the controller's entity.
     */
    public void setRange(double range, Controller controller) {
        this.range = range;
        syncRangeAttribute(controller);
    }

    /**
     * Sets the multiplicative range modifier (1.0 = no modifier) and syncs the Bukkit
     * interaction range attribute. Use this for runtime modifiers (e.g. charms) — unlike
     * setRange, the multiplier survives stat re-application via {@link #apply}.
     */
    public void setRangeMultiplier(double multiplier, Controller controller) {
        this.rangeMultiplier = multiplier;
        syncRangeAttribute(controller);
    }
}