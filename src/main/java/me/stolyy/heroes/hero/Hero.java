package me.stolyy.heroes.hero;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.CooldownManager;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.hero.mechanics.Attributes;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.hero.mechanics.Energy;
import me.stolyy.heroes.hero.mechanics.Jab;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.util.TemplateConfig;
import me.stolyy.heroes.game.GameManager;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class Hero {
    private final String name;
    private final HeroStats baseStats;
    private HeroSkin skin;
    private Controller controller;

    // Mechanics / Components
    private final Attributes attributes;
    private final DoubleJump mobility;
    private final Jab combat;
    private final Energy energy;
    private final CooldownManager cooldowns;

    private final Ability primaryAbility;
    private final Ability secondaryAbility;
    private final Ability smashAbility;
    private final List<Ability> passiveAbilities;

    private final HeroEventBus eventBus;

    // From the hero yml root `visuals.win_sound`; played to everyone at game end via Game (B9).
    // Kill/death jingles are wired onto the event bus in HeroBuilder and need no field here.
    private SoundEffect winSound;

    // A1 lifecycle: an eliminated hero goes DORMANT — it stays registered and keeps ticking
    // its independent abilities (in-flight projectiles, minion reapers) but stops all normal
    // per-tick machinery. Full teardown happens only via cleanup() (logout / game end).
    private boolean eliminated;

    public Hero(String name, HeroStats stats, DoubleJump mobility,
                Ability primary, Ability secondary, Ability smash, List<Ability> passives) {
        this.name = name;
        this.baseStats = stats;

        this.eventBus = new HeroEventBus();
        
        this.attributes = new Attributes(stats.attributes());
        this.combat = new Jab(stats.combat());
        this.energy = new Energy(stats.energy());
        this.cooldowns = new CooldownManager();
        this.mobility = mobility;

        this.primaryAbility = primary;
        this.secondaryAbility = secondary;
        this.smashAbility = smash;
        this.passiveAbilities = passives != null ? passives : new ArrayList<>();

        // Register Abilities to Event Bus
        registerAbility(primaryAbility);
        registerAbility(secondaryAbility);
        registerAbility(smashAbility);
        for (Ability passive : this.passiveAbilities) {
            registerAbility(passive);
        }

        // Register Mechanics Hooks
        this.combat.onHit(victim -> this.triggerJabHit(victim));
        eventBus.onTick(h -> mobility.tick(this));
        
        // Register Default Cooldown Reduction Logic
        registerDefaultPassives();
    }

    private void registerAbility(Ability ability) {
        if (ability == null) return;

        ability.initialize(this);

        eventBus.onJabHit(victim -> ability.onJabHit(this, victim));
        eventBus.onAbilityHit((source, victim) -> ability.onAbilityHit(this, source, victim));
        eventBus.onAbilityUse(source -> ability.onAbilityUse(this, source));
        eventBus.onDamageTaken((source, amount) -> ability.onDamageTaken(this, source, amount));
        eventBus.onKnockbackTaken((source, kb) -> ability.onKnockbackTaken(this, source, kb));
        eventBus.onLand(h -> ability.onLand(this));
        eventBus.onDoubleJump(h -> ability.onDoubleJump(this));
        eventBus.onTick(h -> ability.tick(this));

        eventBus.onDeath(deathLocation -> ability.onDeath(this));
        eventBus.onKill(victim -> ability.onKill(this, victim));
    }

    private void registerDefaultPassives() {
        eventBus.onJabHit(victim -> {
            long ms = Math.round(defaultJabUltChargeMs() * resourceGainFrom(victim));
            if (ms > 0) reduceAbilityCooldown(AbilityType.SMASH, ms);
        });

        // Hurt sound: cooldown + min-damage + chance gated, played to the controller.
        eventBus.onDamageTaken((source, amount) -> {
            if (hurtSound == null || controller == null) return;
            if (amount < hurtSoundMinDamage) return;
            long now = System.currentTimeMillis();
            if (now - lastHurtSoundAt < hurtSoundCooldownMs) return;
            if (hurtSoundChance < 1.0 && Math.random() > hurtSoundChance) return;
            lastHurtSoundAt = now;
            hurtSound.play(controller.getLocation(), controller);
        });

        eventBus.onAbilityHit((ability, victim) -> {
            if (ability.getType() == AbilityType.SMASH) return;
            long ms = Math.round(ability.ultChargeMs(this) * resourceGainFrom(victim));
            if (ms > 0) reduceAbilityCooldown(AbilityType.SMASH, ms);
        });
    }

    public long defaultJabUltChargeMs() {
        return TemplateConfig.requireLong("ability.ult_charge_ms." + attributes.getType().name().toLowerCase() + "_jab");
    }

    public long defaultAbilityUltChargeMs() {
        return TemplateConfig.requireLong("ability.ult_charge_ms." + attributes.getType().name().toLowerCase() + "_ability");
    }

    /**
     * Fraction of on-hit resource gain (ult charge, ability charges, ammo) a hit on {@code victim}
     * grants. Minions decide this through their {@code grants_charge} key; everything else is 1.
     */
    public static double resourceGainFrom(LivingEntity victim) {
        return HeroManager.getHero(victim) instanceof MinionHero minion ? minion.grantsCharge() : 1.0;
    }

    /** Whole-number gains (a charge, a round of ammo) are granted with probability {@link #resourceGainFrom}. */
    public static boolean grantsWholeGain(LivingEntity victim) {
        double fraction = resourceGainFrom(victim);
        return fraction >= 1.0 || (fraction > 0 && Math.random() < fraction);
    }

    public void tick() {
        if (controller == null) return;
        if (eliminated) {
            // Dormant: no energy/cooldown/HUD/mobility ticking for a spectating player —
            // only independent abilities keep resolving. Direct iteration (not the event
            // bus) because the bus can't fire selectively.
            for (Ability a : allAbilities()) {
                if (a.isIndependent()) a.tick(this);
            }
            return;
        }
        energy.tick(controller);
        cooldowns.tick(this, controller);
        eventBus.triggerTick(this);
    }

    private List<Ability> allAbilities() {
        List<Ability> all = new ArrayList<>(passiveAbilities.size() + 3);
        if (primaryAbility != null) all.add(primaryAbility);
        if (secondaryAbility != null) all.add(secondaryAbility);
        if (smashAbility != null) all.add(smashAbility);
        all.addAll(passiveAbilities);
        return all;
    }

    public void triggerAbilityUse(Ability ability) {
        // Any successful ability use forfeits spawn protection — this is the single point every
        // active cast (primary/secondary/smash/passive activations) already flows through.
        if (controller != null && controller.getUniqueId() != null) {
            me.stolyy.heroes.game.SpawnProtection.breakFor(controller.getUniqueId());
        }
        eventBus.triggerAbilityUse(ability);
    }
    public void triggerJabHit(LivingEntity victim) { eventBus.triggerJabHit(victim); }
    public void triggerAbilityHit(Ability ability, LivingEntity victim) { eventBus.triggerAbilityHit(ability, victim); }
    public void triggerDamageTaken(org.bukkit.entity.Entity source, double amount) { eventBus.triggerDamageTaken(source, amount); }

    // Hurt sound (yml `hurt_sound:` block): played to the controller on damage taken,
    // gated by a cooldown, a minimum damage, and a play chance.
    private me.stolyy.heroes.vfx.effect.SoundEffect hurtSound;
    private long hurtSoundCooldownMs;
    private double hurtSoundMinDamage;
    private double hurtSoundChance;
    private long lastHurtSoundAt;

    public void configureHurtSound(me.stolyy.heroes.vfx.effect.SoundEffect sound, long cooldownMs, double minDamage, double chance) {
        this.hurtSound = sound;
        this.hurtSoundCooldownMs = cooldownMs;
        this.hurtSoundMinDamage = minDamage;
        this.hurtSoundChance = chance;
    }
    public void triggerKnockbackTaken(org.bukkit.entity.Entity source, Vector knockback) { eventBus.triggerKnockbackTaken(source, knockback); }
    // Land/jump passives (e.g. Bulk's slam) must not fire off the respawn teleport —
    // gated like usePrimary/useSecondary/onSneak below.
    public void triggerLand() { if (!isRespawning()) eventBus.triggerLand(); }
    public void triggerDoubleJump() { if (!isRespawning()) eventBus.triggerDoubleJump(); }
    public void triggerDeath(Location deathLocation) { eventBus.triggerDeath(deathLocation); }
    /** Convenience for callers with no death location to hand over; the controller has already
     *  moved by then, so this is a best-effort stand-in rather than the real death point. */
    public void triggerDeath() { triggerDeath(controller != null ? controller.getLocation() : null); }
    public void triggerKill(LivingEntity victim) { eventBus.triggerKill(victim); }

    public HeroEventBus getEventBus() { return eventBus; }

    // --- Helper Methods ---

    public void reduceAbilityCooldown(AbilityType type, long ms) {
        Ability ability = switch (type) {
            case PRIMARY -> primaryAbility;
            case SECONDARY -> secondaryAbility;
            case SMASH -> smashAbility;
            default -> null;
        };
        
        if (ability != null) {
            ability.getCooldownObject().reduceCooldown(ms);
        }
    }

    // --- Standard Methods ---

    public void resetStats(Controller controller) {
        attributes.apply(baseStats.attributes());
        attributes.resetMultipliers(); // apply() only restores stat-backed values
        attributes.applyAttributes(controller);
        mobility.apply(baseStats.mobility());
        mobility.resetJumps();
        combat.apply(baseStats.combat(), controller);
        energy.apply(baseStats.energy());
        energy.setEnergy(baseStats.energy().startingEnergy());
        cooldowns.resetAll();
    }
    
    public void applyAttributes(Controller controller) {
        attributes.applyAttributes(controller);
    }

    public Attributes getAttributes() { return attributes; }
    public DoubleJump getMobility() { return mobility; }
    public Jab getCombat() { return combat; }
    public Energy getEnergy() { return energy; }
    public CooldownManager getCooldowns() { return cooldowns; }

    public String getName() { return name; }
    public HeroStats getBaseStats() { return baseStats; }
    public HeroSkin getSkin() { return skin; }
    public void setSkin(HeroSkin skin) { this.skin = skin; }
    public SoundEffect getWinSound() { return winSound; }
    public void setWinSound(SoundEffect winSound) { this.winSound = winSound; }
    public Controller getController() { return controller; }
    public void setController(Controller controller) { this.controller = controller; }

    public Ability getPrimaryAbility() { return primaryAbility; }
    public Ability getSecondaryAbility() { return secondaryAbility; }
    public Ability getSmashAbility() { return smashAbility; }
    public List<Ability> getPassiveAbilities() { return passiveAbilities; }

    // --- Lifecycle Methods ---
    //
    // A1 lifecycle contract:
    //  - death/respawn      -> triggerDeath() only (onDeath fires; bus + detached state live)
    //  - elimination        -> eliminate(): dormant flag then triggerDeath() (NO teardown —
    //                          projectiles/minions persist, bus stays live for kill credit)
    //  - logout / game end  -> cleanup(): the ONLY full-teardown path

    /**
     * Full teardown — reference-lost (logout) and game-end paths ONLY. Never called on
     * death, respawn, or elimination. Stops character-bound actives via onDeath, expires
     * detached state via onOwnerLost, then silences the bus. Direct ability iteration
     * (not triggerDeath) so bus-level death listeners (e.g. the death jingle) don't fire.
     */
    public void cleanup() {
        for (Ability a : allAbilities()) {
            a.onDeath(this);      // stop character-bound actives (dashes, vehicles, duration ults)
            a.onOwnerLost(this);  // expire detached state (in-flight projectiles, planted objects)
        }
        eventBus.clear();
        if (controller != null && controller.getEntity() != null) {
            resetStats(controller);
        }
    }

    /**
     * Elimination: go dormant and fire death hooks. Teardown waits for logout/game end.
     * The flag is set BEFORE the hooks run so a throwing cosmetic listener can never leave a
     * hero un-eliminated and still ticking as a live participant.
     */
    public void eliminate(Location deathLocation) {
        eliminated = true;
        triggerDeath(deathLocation);
    }

    /** Convenience overload; see {@link #triggerDeath()} for the location caveat. */
    public void eliminate() { eliminate(controller != null ? controller.getLocation() : null); }

    public boolean isEliminated() { return eliminated; }

    private Runnable primaryOverride;

    /** Arms a one-shot replacement for the next primary input; consumed on use. */
    public void setPrimaryOverride(Runnable action) { this.primaryOverride = action; }
    public void clearPrimaryOverride() { this.primaryOverride = null; }

    // Full input lock (Mr. Monster's grab holds BOTH participants in it): every ability
    // input and jab is dead while set. The one exception is a pending primary override —
    // deliberately, because the override IS the grab's throw input.
    private boolean actionsLocked;

    public boolean actionsLocked() { return actionsLocked; }
    public void setActionsLocked(boolean locked) { this.actionsLocked = locked; }

    // Post-override input absorption: consuming a primary override (the grab's throw) unlocks
    // actions, but the SAME physical click keeps generating use inputs for a few ticks (the
    // held-fire sampler, the paired interact events) — which fired the real primary right on
    // top of the throw. The override consumer stamps a short lockout to swallow that residue.
    private int primarySuppressedUntilTick = Integer.MIN_VALUE;

    public void suppressPrimary(int ticks) {
        primarySuppressedUntilTick = org.bukkit.Bukkit.getCurrentTick() + ticks;
    }

    public void usePrimary() {
        if (isRespawning()) return;
        // One-shot intercept (Clusterbuck arm-then-fire): the armed action REPLACES the
        // normal primary for exactly one input. Checked BEFORE the action lock — the
        // grab's throw rides this seam and must fire while everything else is locked.
        if (primaryOverride != null) {
            Runnable action = primaryOverride;
            primaryOverride = null;
            action.run();
            return;
        }
        if (actionsLocked) return;
        if (org.bukkit.Bukkit.getCurrentTick() < primarySuppressedUntilTick) return;
        if (primaryAbility != null && primaryAbility.execute(this)) triggerAbilityUse(primaryAbility);
    }
    public void useSecondary() { if (!isRespawning() && !actionsLocked && secondaryAbility != null && secondaryAbility.execute(this)) triggerAbilityUse(secondaryAbility); }
    public void useSmash() { if (!isRespawning() && !actionsLocked && smashAbility != null && smashAbility.execute(this)) triggerAbilityUse(smashAbility); }

    public void onSneak() { if (!isRespawning() && !actionsLocked) triggerPassives(AbilityType.PASSIVE_SNEAK); }
    public void onDrop() {
        if (isRespawning() || actionsLocked) return;
        // Q-reload: a partially spent CLIP primary dumps its rounds and starts the timed
        // reload (startReload no-ops for full clips, in-progress reloads, and non-CLIP
        // modes). PASSIVE_DROP still fires after.
        if (primaryAbility instanceof BaseAbility base && base.getAmmo() != null) {
            // Same audio cue as the natural empty-clip reload (BaseAbility.handleAmmo).
            if (base.getAmmo().startReload() && base.getAmmo().getReloadSound() != null) {
                base.getAmmo().getReloadSound().play(controller);
            }
        }
        triggerPassives(AbilityType.PASSIVE_DROP);
    }
    public void onSwap() { if (!isRespawning() && !actionsLocked) triggerPassives(AbilityType.PASSIVE_SWAP); }

    /** Public: the mobility layer (DoubleJump and its variants) shares this gate too. */
    public boolean isRespawning() {
        if (controller != null && controller.getEntity() instanceof Player p) {
            return GameManager.isRespawning(p);
        }
        return false;
    }
    
    private void triggerPassives(AbilityType type) {
        for (Ability passive : passiveAbilities) {
            if (passive.getType() == type) {
                passive.execute(this);
                triggerAbilityUse(passive);
            }
        }
    }
}
