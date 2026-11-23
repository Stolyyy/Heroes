package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

public abstract class BaseAbility implements Ability {
    protected String name;
    protected String description;
    protected AbilityType type;
    protected Cooldown cooldown;
    protected long activeDuration;
    protected Ammo ammo;
    protected ConfigurationSection config;
    protected double damage;
    protected double knockback;
    protected double radius;
    protected long windupMs;
    private long windupEndsAt;
    private Long ultChargeMsOverride;

    public BaseAbility(ConfigurationSection config, HeroSkin skin) {
        if (config == null) return;

        String typeStr = config.getString("type", "PRIMARY");
        try {
            this.type = AbilityType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.type = AbilityType.PRIMARY;
        }

        // 2. Merge skin overrides using the type as the key
        this.config = config;
        if (skin != null) {
            ConfigurationSection override = skin.getAbilityOverride(this.type.name());
            if (override != null) {
                this.config = mergeConfigs(config, override);
            }
        }

        this.name = this.config.getString("name", "Unknown Ability");
        this.description = this.config.getString("description", "");
        
        double cdSeconds = this.config.getDouble("cooldown", 0.0);
        this.activeDuration = (long) (this.config.getDouble("active_duration", 0.0) * 1000);
        
        this.cooldown = new Cooldown((long)(cdSeconds * 1000), this.config.getInt("energy_cost", 0), this.config.getInt("charges", 0));

        this.damage = this.config.getDouble("damage", 0);
        this.knockback = this.config.getDouble("knockback", 0);
        this.radius = this.config.getDouble("radius", 0);
        this.windupMs = (long) (Configs.orTemplate(this.config, "windup", "ability.windup") * 1000);
        this.ultChargeMsOverride = this.config.contains("ult_charge_ms") ? this.config.getLong("ult_charge_ms") : null;

        if (this.config.contains("ammo")) {
            ConfigurationSection ammoSec = this.config.getConfigurationSection("ammo");
            Ammo.Mode mode = Ammo.Mode.valueOf(ammoSec.getString("mode", "CLIP").toUpperCase());
            int max = ammoSec.getInt("max", 1);
            long regen = (long) (ammoSec.getDouble("regen", cdSeconds) * 1000);
            SoundEffect reloadSound = new SoundEffect(ammoSec.getConfigurationSection("reload_sound"));
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
    public boolean execute(Hero hero) {
        if(!canExecute(hero)) return false;
        if(ammo != null)  return handleAmmo(hero);
        startCooldown(hero);
        performAfterWindup(hero);
        return true;
    }

    /** Cost and cooldown are already paid; the ability lands now, or after {@code windup} seconds via {@link #tick}. */
    private void performAfterWindup(Hero hero) {
        if (windupMs <= 0) {
            perform(hero);
            return;
        }
        windupEndsAt = System.currentTimeMillis() + windupMs;
        onWindupStart(hero);
    }

    public boolean isWindingUp() { return windupEndsAt > 0; }

    protected void onWindupStart(Hero hero) {}

    /**
     * Template lifecycle, not overridable: resolves a pending windup, then hands the tick to the
     * ability through {@link #onTick}. Final so windup and any future template bookkeeping run for
     * every ability regardless of what the kit overrides.
     */
    @Override
    public final void tick(Hero hero) {
        if (windupEndsAt > 0 && System.currentTimeMillis() >= windupEndsAt) {
            windupEndsAt = 0;
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
     * Default death cleanup: if the ability is currently in its active phase, reset the
     * cooldown (ends the active phase and starts the cooldown timer). CLIP-ammo primaries
     * also refill so the player respawns with a full clip. Overrides should call
     * {@code super.onDeath(hero)} to preserve this behavior, then add any ability-specific
     * state cleanup on top.
     */
    @Override
    public void onDeath(Hero hero) {
        windupEndsAt = 0;
        if (cooldown != null && cooldown.isInUse()) {
            cooldown.reset();
        }
        // Auto-reload on death (B5): respawn with a full clip.
        if (type == AbilityType.PRIMARY && ammo != null && ammo.getMode() == Ammo.Mode.CLIP) {
            ammo.setAmmo(ammo.getMax());
        }
    }

    @Override
    public boolean canExecute(Hero hero){
        int currentEnergy = (int) hero.getEnergy().getCurrentEnergy();
        if (!cooldown.isReady(currentEnergy)) {
            // Primaries fail silently — the action bar already shows their cooldown,
            // and the held-fire sampler probes this every tick.
            if (type != AbilityType.PRIMARY) cooldown.sendNotReadyVisuals(hero, getName());
            return false;
        }
        if(ammo != null) return hasAmmo(hero);
        return true;
    }

    public boolean hasAmmo(Hero hero){
        if (!ammo.isReady()) {
            if (ammo.getMode() == Ammo.Mode.CLIP && type != AbilityType.PRIMARY) {
                cooldown.sendNotReadyVisuals(hero, getName());
            }
            return false;
        }
        return ammo.canConsume();
    }

    protected boolean handleAmmo(Hero hero) {
        ammo.tryConsume();
        boolean triggerCooldown = (ammo.getMode() == Ammo.Mode.CLIP && ammo.getCurrent() == 0) || (ammo.getMode() == Ammo.Mode.POWER);
        if (triggerCooldown) startCooldown(hero);
        else cooldown.start(activeDuration);
        if (triggerCooldown && ammo.getReloadSound() != null) ammo.getReloadSound().play(hero.getController());
        performAfterWindup(hero);
        return true;
    }

    protected void startCooldown(Hero hero) {
        if (getEnergyCost() > 0) hero.getEnergy().tryConsume(hero.getController(), getEnergyCost());
        cooldown.start(activeDuration);
    }

    public abstract void perform(Hero hero);

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public AbilityType getType() { return type; }
    @Override public long getCooldown() { return cooldown.getCooldownDuration(); }
    @Override public int getEnergyCost() { return cooldown.getEnergyCost(); }
    @Override public Cooldown getCooldownObject() { return cooldown; }
    public Ammo getAmmo() { return ammo; }

    @Override public void setDamage(double damage) { this.damage = damage; }
    @Override public double getDamage() { return damage; }
    @Override public void setKnockback(double knockback) { this.knockback = knockback; }
    @Override public double getKnockback() { return knockback; }
    @Override public void setRadius(double radius) { this.radius = radius; }
    @Override public double getRadius() { return radius; }

    @Override public void onHit(Hero attacker, org.bukkit.entity.LivingEntity victim) {
        attacker.triggerAbilityHit(this, victim);
    }
    
    @Override public void onHitWall(Hero hero, org.bukkit.Location location) {}
    @Override public void onExpire(Hero hero, org.bukkit.Location location) {}
    @Override public void onJabHit(Hero hero, org.bukkit.entity.LivingEntity victim) {}
    @Override public void onAbilityHit(Hero hero, Ability ability, org.bukkit.entity.LivingEntity victim) {}
    @Override public void onAbilityUse(Hero hero, Ability ability) {}
    @Override public void onDamageTaken(Hero hero, org.bukkit.entity.Entity source, double amount) {}
    @Override public void onKnockbackTaken(Hero hero, org.bukkit.entity.Entity source, org.bukkit.util.Vector knockback) {}
    @Override public void onLand(Hero hero) {}

    @Override public void setCooldown(long ms) { this.cooldown.setCooldownDuration(ms); }
    @Override public void setEnergyCost(int cost) { this.cooldown.setEnergyCost(cost); }
}
    