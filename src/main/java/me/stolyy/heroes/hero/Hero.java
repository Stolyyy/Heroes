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
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.util.TemplateConfig;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.SpawnProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Hero {
    private final String name;
    private final HeroStats baseStats;
    private HeroSkin skin;
    private HeroSkin baseSkin;
    private Controller controller;

    // Mechanics / Components
    private final Attributes attributes;
    private final DoubleJump mobility;
    private final Jab combat;
    private final Energy energy;
    private final CooldownManager cooldowns;
    private final StatusEffects status = new StatusEffects();

    private final Ability primaryAbility;
    private final Ability secondaryAbility;
    private final Ability smashAbility;
    private final List<Ability> passiveAbilities;

    private final HeroEventBus eventBus;

    // From the hero yml root `visuals.win_sound`; played to everyone at game end via Game.
    // Kill/death jingles are wired onto the event bus in HeroBuilder and need no field here.
    private SoundEffect winSound;

    // Hurt sound (yml `hurt_sound:` block): played to the controller on damage taken,
    // gated by a cooldown, a minimum damage, and a play chance.
    private SoundEffect hurtSound;
    private long hurtSoundCooldownMs;
    private double hurtSoundMinDamage;
    private double hurtSoundChance;
    private long lastHurtSoundAt;

    // Lifecycle: an eliminated hero goes DORMANT — it stays registered and keeps ticking
    // its independent abilities (in-flight projectiles, minion reapers) but stops all normal
    // per-tick machinery. Full teardown happens only via cleanup() (logout / game end).
    private boolean eliminated;

    private Runnable primaryOverride;
    // Post-override input absorption: consuming a primary override (the grab's throw) clears
    // the grab's disable status, but the SAME physical click keeps generating use inputs for a few ticks (the
    // held-fire sampler, the paired interact events) — which fired the real primary right on
    // top of the throw. The override consumer stamps a short lockout to swallow that residue.
    private int primarySuppressedUntilTick = Integer.MIN_VALUE;

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
            mobility.tickDormant(this);
            return;
        }
        energy.tick(controller);
        cooldowns.tick(this, controller);
        eventBus.triggerTick(this);
        status.tick(this);
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
            SpawnProtection.breakFor(controller.getUniqueId());
        }
        eventBus.triggerAbilityUse(ability);
    }
    public void triggerJabHit(LivingEntity victim) { eventBus.triggerJabHit(victim); }
    public void triggerAbilityHit(Ability ability, LivingEntity victim) { eventBus.triggerAbilityHit(ability, victim); }
    public void triggerDamageTaken(Entity source, double amount) { eventBus.triggerDamageTaken(source, amount); }

    public void configureHurtSound(SoundEffect sound, long cooldownMs, double minDamage, double chance) {
        this.hurtSound = sound;
        this.hurtSoundCooldownMs = cooldownMs;
        this.hurtSoundMinDamage = minDamage;
        this.hurtSoundChance = chance;
    }
    public void triggerKnockbackTaken(Entity source, Vector knockback) { eventBus.triggerKnockbackTaken(source, knockback); }
    // Land/jump passives (e.g. Bulk's slam) must not fire off the respawn teleport —
    // gated like usePrimary/useSecondary/onSneak below.
    public void triggerLand() { if (!isRespawning()) eventBus.triggerLand(); }
    public void triggerDoubleJump() { if (!isRespawning()) eventBus.triggerDoubleJump(); }
    public void triggerDeath(Location deathLocation) {
        status.clear(this);
        mobility.onDeath(this);
        eventBus.triggerDeath(deathLocation);
    }
    public void triggerKill(LivingEntity victim) { eventBus.triggerKill(victim); }

    public HeroEventBus getEventBus() { return eventBus; }

    // --- Helper Methods ---

    public void reduceAbilityCooldown(AbilityType type, long ms) {
        Ability ability = switch (type) {
            case PRIMARY -> primaryAbility;
            case SECONDARY -> secondaryAbility;
            case SMASH -> smashAbility;
            case PASSIVE, PASSIVE_SNEAK, PASSIVE_DROP, PASSIVE_SWAP ->
                    throw new IllegalArgumentException("Cannot reduce cooldown for non-slot type: " + type);
        };
        
        if (ability != null) {
            ability.getCooldownObject().reduceCooldown(ms);
        }
    }

    // --- Standard Methods ---

    public void resetStats(Controller controller) {
        attributes.apply(baseStats.attributes());
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
    public StatusEffects status() { return status; }

    public String getName() { return name; }
    public HeroStats getBaseStats() { return baseStats; }
    public HeroSkin getSkin() { return skin; }
    public void setSkin(HeroSkin skin) { this.skin = skin; }
    /** The skin fixed when this hero was built for its controller; transient ability skins revert to it. */
    public HeroSkin getBaseSkin() { return baseSkin; }
    public void setBaseSkin(HeroSkin baseSkin) { this.baseSkin = baseSkin; }
    public SoundEffect getWinSound() { return winSound; }
    public void setWinSound(SoundEffect winSound) { this.winSound = winSound; }
    public Controller getController() { return controller; }
    public void setController(Controller controller) { this.controller = controller; }

    public Ability getPrimaryAbility() { return primaryAbility; }
    public Ability getSecondaryAbility() { return secondaryAbility; }
    public Ability getSmashAbility() { return smashAbility; }
    public List<Ability> getPassiveAbilities() { return passiveAbilities; }
    public List<Ability> getAbilities() { return allAbilities(); }

    public <T extends Ability> Optional<T> findAbility(Class<T> type) {
        for (Ability ability : allAbilities()) {
            if (type.isInstance(ability)) return Optional.of(type.cast(ability));
        }
        return Optional.empty();
    }

    public <T extends Ability> T requireAbility(Class<T> type) {
        return findAbility(type).orElseThrow(() -> new IllegalStateException(
                "Hero '" + name + "' is missing required ability " + type.getSimpleName()));
    }

    // --- Lifecycle Methods ---
    //
    // Lifecycle contract:
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
        status.clear(this);
        for (Ability a : allAbilities()) {
            a.onDeath(this);      // stop character-bound actives (dashes, vehicles, duration ults)
            a.onOwnerLost(this);  // expire detached state (in-flight projectiles, planted objects)
        }
        mobility.onDeath(this);
        mobility.onOwnerLost(this);
        eventBus.clear();
        attributes.clearModifiers();
        energy.clearModifiers();
        combat.clearModifiers();
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

    public boolean isEliminated() { return eliminated; }

    /** Arms a one-shot replacement for the next primary input; consumed on use. */
    public void setPrimaryOverride(Runnable action) { this.primaryOverride = action; }
    public void clearPrimaryOverride() { this.primaryOverride = null; }

    public void suppressPrimary(int ticks) {
        primarySuppressedUntilTick = Bukkit.getCurrentTick() + ticks;
    }

    public void usePrimary() {
        if (isRespawning()) return;
        // One-shot intercept (Clusterbuck arm-then-fire): the armed action REPLACES the
        // normal primary for exactly one input. Checked BEFORE the ability restriction — the
        // grab's throw rides this seam and must fire while disabled.
        if (primaryOverride != null) {
            Runnable action = primaryOverride;
            primaryOverride = null;
            action.run();
            return;
        }
        if (status.blocks(StatusEffects.Restriction.ABILITIES)) return;
        if (Bukkit.getCurrentTick() < primarySuppressedUntilTick) return;
        if (primaryAbility != null && primaryAbility.execute(this)) triggerAbilityUse(primaryAbility);
    }
    public void useSecondary() { if (!isRespawning() && !status.blocks(StatusEffects.Restriction.ABILITIES) && secondaryAbility != null && secondaryAbility.execute(this)) triggerAbilityUse(secondaryAbility); }
    public void useSmash() { if (!isRespawning() && !status.blocks(StatusEffects.Restriction.ABILITIES) && smashAbility != null && smashAbility.execute(this)) triggerAbilityUse(smashAbility); }

    public void onSneak() { if (!isRespawning() && !status.blocks(StatusEffects.Restriction.ABILITIES)) triggerPassives(AbilityType.PASSIVE_SNEAK); }
    public void onDrop() {
        if (isRespawning() || status.blocks(StatusEffects.Restriction.ABILITIES)) return;
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
    public void onSwap() { if (!isRespawning() && !status.blocks(StatusEffects.Restriction.ABILITIES)) triggerPassives(AbilityType.PASSIVE_SWAP); }

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
                if (passive.execute(this)) {
                    triggerAbilityUse(passive);
                }
            }
        }
    }
}
