package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.ModifierStack;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * Base template for all abilities. Owns final {@link #execute}, {@link #canExecute}, and
 * {@link #tick} lifecycle hooks, windup resolution, ammo/energy payment, deferred cooldowns,
 * and keyed damage/knockback/radius modifier stacks; subclasses implement {@link #perform} and
 * optional hooks ({@link #canUse}, {@link #onCast}, {@link #onTick}, {@link #onHit}).
 */
public abstract class BaseAbility implements Ability {
    protected enum CooldownStart { CAST, RESOLVE }

    protected String name;
    protected String description;
    protected AbilityType type;
    protected Cooldown cooldown;
    protected long activeDuration;
    protected Ammo ammo;
    protected ConfigurationSection config;
    private double baseDamage;
    private double baseKnockback;
    private double baseRadius;
    protected double damage;
    protected double knockback;
    protected double radius;
    private final ModifierStack damageMods = new ModifierStack();
    private final ModifierStack knockbackMods = new ModifierStack();
    private final ModifierStack radiusMods = new ModifierStack();
    protected long windupMs;
    protected final boolean deferCooldown;
    protected final long missCooldownMs;
    private final boolean chargeOnPrimaryHit;
    private long windupEndsAt;
    private boolean pendingCooldownStart;
    private boolean awaitingCooldownResolve;
    private Long ultChargeMsOverride;

    public BaseAbility(ConfigurationSection config, HeroSkin skin) {
        this(config, skin, null);
    }

    /**
     * Sub-ability constructor for composed kits that own inner template instances (e.g.
     * {@code Botclaw}'s bombs, {@code BugSpell}'s directional variants). Supplying
     * {@code explicitType} avoids mutating the shared config tree and skips slot-level skin
     * overrides intended for the outer ability.
     */
    protected BaseAbility(ConfigurationSection config, HeroSkin skin, AbilityType explicitType) {
        if (config == null) {
            throw new IllegalArgumentException("BaseAbility config section cannot be null");
        }

        if (explicitType != null) {
            this.type = explicitType;
            this.config = config;
        } else {
            String typeStr = Configs.requireString(config, "type");
            this.type = AbilityType.valueOf(typeStr.toUpperCase());

            // Merge skin overrides using the slot type as the key
            this.config = config;
            if (skin != null) {
                ConfigurationSection override = skin.getAbilityOverride(this.type.name());
                if (override != null) {
                    this.config = mergeConfigs(config, override);
                }
            }
        }

        this.name = Configs.orTemplateString(this.config, "name", "ability.name");
        this.description = Configs.orTemplateString(this.config, "description", "ability.description");

        double cdSeconds = Configs.orTemplate(this.config, "cooldown", "ability.cooldown");
        this.activeDuration = (long) (Configs.orTemplate(this.config, "active_duration", "ability.active_duration") * 1000);

        this.cooldown = new Cooldown(
                (long) (cdSeconds * 1000),
                Configs.orTemplateInt(this.config, "energy_cost", "ability.energy_cost"),
                Configs.orTemplateInt(this.config, "charges", "ability.charges"));

        this.baseDamage = Configs.orTemplate(this.config, "damage", "ability.damage");
        this.baseKnockback = Configs.orTemplate(this.config, "knockback", "ability.knockback");
        this.baseRadius = Configs.orTemplate(this.config, "radius", "ability.radius");
        this.damage = this.baseDamage;
        this.knockback = this.baseKnockback;
        this.radius = this.baseRadius;
        this.windupMs = (long) (Configs.orTemplate(this.config, "windup", "ability.windup") * 1000);
        CooldownStart cdStart = CooldownStart.valueOf(
                Configs.orTemplateString(this.config, "cooldown_starts", "ability.cooldown_starts").toUpperCase());
        this.deferCooldown = cdStart == CooldownStart.RESOLVE;
        double missSec = Configs.orTemplate(this.config, "miss_cooldown", "ability.miss_cooldown");
        this.missCooldownMs = missSec >= 0 ? (long) (missSec * 1000) : -1L;
        this.chargeOnPrimaryHit = Configs.orTemplateBoolean(
                this.config, "charge_on_primary_hit", "ability.charge_on_primary_hit");
        this.ultChargeMsOverride = this.config.contains("ult_charge_ms")
                ? Configs.requireLong(this.config, "ult_charge_ms") : null;

        if (this.config.contains("ammo")) {
            ConfigurationSection ammoSec = Configs.requireSection(this.config, "ammo");
            Ammo.Mode mode = Ammo.Mode.valueOf(Configs.requireString(ammoSec, "mode").toUpperCase());
            int max = Configs.requireInt(ammoSec, "max");
            long regen = (long) (Configs.requireDouble(ammoSec, "regen") * 1000);
            SoundEffect reloadSound = SoundEffect.optional(ammoSec, "reload_sound");
            this.ammo = new Ammo(mode, max, regen, reloadSound);
        }
    }

    private ConfigurationSection mergeConfigs(ConfigurationSection base, ConfigurationSection override) {
        MemoryConfiguration merged = new MemoryConfiguration();
        for (String key : base.getKeys(true)) {
            merged.set(key, base.get(key));
        }
        for (String key : override.getKeys(true)) {
            merged.set(key, override.get(key));
        }
        return merged;
    }

    @Override
    public final boolean execute(Hero hero) {
        if (canRecast(hero)) return tryRecast(hero);
        if (!canExecute(hero)) return false;
        onCast(hero);
        long windup = windupMs(hero);
        if (ammo != null) return handleAmmo(hero, windup);
        if (getEnergyCost() > 0) hero.getEnergy().tryConsume(hero.getController(), getEnergyCost());
        if (windup > 0) {
            if (deferCooldown) awaitingCooldownResolve = true;
            beginWindup(hero, windup, !deferCooldown);
        } else {
            if (deferCooldown) awaitingCooldownResolve = true;
            else cooldown.start(activeDurationFor(hero));
            perform(hero);
        }
        return true;
    }

    private void beginWindup(Hero hero, long durationMs, boolean startCooldownOnResolve) {
        windupEndsAt = System.currentTimeMillis() + durationMs;
        pendingCooldownStart = startCooldownOnResolve;
        onWindupStart(hero);
    }

    public boolean isWindingUp() { return windupEndsAt > 0; }

    protected boolean canUse(Hero hero) { return true; }

    protected boolean canRecast(Hero hero) { return false; }

    protected boolean tryRecast(Hero hero) { return false; }

    /** Called immediately on cast acceptance before windup or cooldown begins. */
    protected void onCast(Hero hero) {}

    protected long windupMs(Hero hero) { return windupMs; }

    protected long activeDurationFor(Hero hero) { return activeDuration; }

    protected void onWindupStart(Hero hero) {}

    /** Starts the full cooldown (for {@code cooldown_starts: RESOLVE} abilities). */
    protected void resolveCooldown(Hero hero) {
        if (deferCooldown && !awaitingCooldownResolve) return;
        awaitingCooldownResolve = false;
        cooldown.start(activeDurationFor(hero));
    }

    /** Starts the shorter {@code miss_cooldown} when a deferred-cooldown cast whiffs. */
    protected void resolveMissCooldown() {
        if (deferCooldown && !awaitingCooldownResolve) return;
        awaitingCooldownResolve = false;
        cooldown.start(0);
        if (missCooldownMs >= 0) {
            cooldown.reduceCooldown(Math.max(0, cooldown.getCooldownDuration() - missCooldownMs));
        }
    }

    /**
     * Template lifecycle, not overridable: resolves a pending windup (starting active_duration and
     * cooldown after windup completes), then hands the tick to {@link #onTick}.
     */
    @Override
    public final void tick(Hero hero) {
        if (windupEndsAt > 0 && System.currentTimeMillis() >= windupEndsAt) {
            windupEndsAt = 0;
            if (pendingCooldownStart) {
                pendingCooldownStart = false;
                cooldown.start(activeDurationFor(hero));
            }
            perform(hero);
        }
        onTick(hero);
    }

    protected void onTick(Hero hero) {}

    /** SMASH cooldown removed when this ability lands a hit; the yml key {@code ult_charge_ms} overrides the hero-type default. */
    @Override
    public long ultChargeMs(Hero hero) {
        return ultChargeMsOverride != null ? ultChargeMsOverride : hero.defaultAbilityUltChargeMs();
    }

    /**
     * Default death cleanup: clears pending windup, resets an active cooldown window, and refills
     * CLIP-ammo primaries so players respawn with a full clip.
     */
    @Override
    public void onDeath(Hero hero) {
        if (windupEndsAt > 0) {
            windupEndsAt = 0;
            if (pendingCooldownStart) {
                pendingCooldownStart = false;
                cooldown.start(0);
            }
        }
        if (awaitingCooldownResolve) {
            awaitingCooldownResolve = false;
            cooldown.start(0);
        }
        if (cooldown != null && cooldown.isInUse()) {
            cooldown.reset();
        }
        // Auto-reload on death: respawn with a full clip.
        if (type == AbilityType.PRIMARY && ammo != null && ammo.getMode() == Ammo.Mode.CLIP) {
            ammo.setAmmo(ammo.getMax());
        }
    }

    @Override
    public final boolean canExecute(Hero hero) {
        if (canRecast(hero)) return true;
        if (isWindingUp() || !canUse(hero)) return false;
        int currentEnergy = (int) hero.getEnergy().getCurrentEnergy();
        if (!cooldown.isReady(currentEnergy)) {
            // Primaries fail silently — the action bar already shows their cooldown,
            // and the held-fire sampler probes this every tick.
            if (type != AbilityType.PRIMARY) cooldown.sendNotReadyVisuals(hero, getName());
            return false;
        }
        if (ammo != null) return hasAmmo(hero);
        return true;
    }

    public boolean hasAmmo(Hero hero) {
        if (!ammo.isReady()) {
            if (ammo.getMode() == Ammo.Mode.CLIP && type != AbilityType.PRIMARY) {
                cooldown.sendNotReadyVisuals(hero, getName());
            }
            return false;
        }
        return ammo.canConsume();
    }

    private boolean handleAmmo(Hero hero, long windup) {
        ammo.tryConsume();
        boolean triggerCooldown = (ammo.getMode() == Ammo.Mode.CLIP && ammo.getCurrent() == 0)
                || (ammo.getMode() == Ammo.Mode.POWER);
        if (triggerCooldown && ammo.getReloadSound() != null) ammo.getReloadSound().play(hero.getController());
        if (triggerCooldown && getEnergyCost() > 0) hero.getEnergy().tryConsume(hero.getController(), getEnergyCost());
        if (windup > 0) {
            if (deferCooldown) awaitingCooldownResolve = true;
            beginWindup(hero, windup, !deferCooldown);
        } else {
            if (deferCooldown) awaitingCooldownResolve = true;
            else cooldown.start(activeDurationFor(hero));
            perform(hero);
        }
        return true;
    }

    /**
     * Hit pipeline entrypoint: fires {@link Hero#triggerAbilityHit} BEFORE building the interaction
     * so on-hit passives (e.g. CryomancerFrostAura) can adjust damage before it is read.
     */
    protected Interactions.InteractionBuilder hit(Hero attacker, LivingEntity victim) {
        attacker.triggerAbilityHit(this, victim);
        return Interactions.on(attacker.getController().getEntity(), victim).abilityName(getName());
    }

    protected double calcDamage(Hero hero, LivingEntity victim) { return damage; }
    protected double calcKnockback(Hero hero, LivingEntity victim) { return knockback; }

    public abstract void perform(Hero hero);

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public AbilityType getType() { return type; }
    @Override public long getCooldown() { return cooldown.getCooldownDuration(); }
    @Override public int getEnergyCost() { return cooldown.getEnergyCost(); }
    @Override public Cooldown getCooldownObject() { return cooldown; }
    public Ammo getAmmo() { return ammo; }

    protected void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
        recomputeDamage();
    }

    protected void setBaseKnockback(double baseKnockback) {
        this.baseKnockback = baseKnockback;
        recomputeKnockback();
    }

    protected void setBaseRadius(double baseRadius) {
        this.baseRadius = baseRadius;
        recomputeRadius();
    }

    private void recomputeDamage() { this.damage = damageMods.evaluate(baseDamage, 0.0); }
    private void recomputeKnockback() { this.knockback = knockbackMods.evaluate(baseKnockback, 0.0); }
    private void recomputeRadius() { this.radius = radiusMods.evaluate(baseRadius, 0.0); }

    @Override public void addDamageMultiplier(Object key, double mult) { damageMods.putMult(key, mult); recomputeDamage(); }
    @Override public void addDamageFlat(Object key, double delta) { damageMods.putFlat(key, delta); recomputeDamage(); }
    @Override public void removeDamageModifier(Object key) { damageMods.remove(key); recomputeDamage(); }
    @Override public double getDamage() { return damage; }

    @Override public void addKnockbackMultiplier(Object key, double mult) { knockbackMods.putMult(key, mult); recomputeKnockback(); }
    @Override public void addKnockbackFlat(Object key, double delta) { knockbackMods.putFlat(key, delta); recomputeKnockback(); }
    @Override public void removeKnockbackModifier(Object key) { knockbackMods.remove(key); recomputeKnockback(); }
    @Override public double getKnockback() { return knockback; }

    @Override public void addRadiusMultiplier(Object key, double mult) { radiusMods.putMult(key, mult); recomputeRadius(); }
    @Override public void addRadiusFlat(Object key, double delta) { radiusMods.putFlat(key, delta); recomputeRadius(); }
    @Override public void removeRadiusModifier(Object key) { radiusMods.remove(key); recomputeRadius(); }
    @Override public double getRadius() { return radius; }

    @Override public void onHit(Hero attacker, LivingEntity victim) {}

    @Override public void onHitWall(Hero hero, Location location) {}
    @Override public void onExpire(Hero hero, Location location) {}
    @Override public void onJabHit(Hero hero, LivingEntity victim) {}
    @Override public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        if (chargeOnPrimaryHit && ability.getType() == AbilityType.PRIMARY && Hero.grantsWholeGain(victim)) {
            cooldown.charge(1);
        }
    }
    @Override public void onAbilityUse(Hero hero, Ability ability) {}
    @Override public void onDamageTaken(Hero hero, Entity source, double amount) {}
    @Override public void onKnockbackTaken(Hero hero, Entity source, Vector knockback) {}
    @Override public void onLand(Hero hero) {}
}
    