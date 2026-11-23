package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.VectorUtil;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.Cone;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class PunishgersWeapon extends BaseAbility {
    private final MinigunMode minigun;
    private final ShotgunMode shotgun;
    private final double movementPenaltyMultiplier;
    private final int penaltyLingerTicks;
    private final int pulloutTicks;
    private final int swapReloadTicks;

    private PunishgersSwap swap;
    private boolean penaltyActive;
    private int ticksSinceLastShot;
    private long swapEndsAt;

    private boolean unlimitedAmmo;
    private boolean penaltyDisabled;

    public PunishgersWeapon(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.minigun = new MinigunMode(this.config.getConfigurationSection("minigun"), skin);
        this.shotgun = new ShotgunMode(this.config.getConfigurationSection("shotgun"), skin);
        this.movementPenaltyMultiplier = Configs.requireDouble(this.config, "movement_penalty_multiplier");
        this.penaltyLingerTicks = Configs.requireInt(this.config, "penalty_linger_ticks");
        this.pulloutTicks = Configs.requireInt(this.config, "pullout_ticks");
        this.swapReloadTicks = Configs.requireInt(this.config, "swap_reload_ticks");
    }

    @Override
    public void initialize(Hero hero) {
        this.swap = PunishgersSwap.find(hero);
    }

    private BaseAbility activeGun() {
        boolean shotgunMode = swap != null && swap.getMode() == PunishgersSwap.WeaponMode.SHOTGUN;
        return shotgunMode ? shotgun : minigun;
    }

    @Override
    public Ammo getAmmo() {
        return activeGun().getAmmo();
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (cooldown.isInUse() || isUltWindingUp(hero)) return false;
        return activeGun().canExecute(hero);
    }

    private static boolean isUltWindingUp(Hero hero) {
        return hero.getSmashAbility() instanceof BaseAbility ult && ult.isWindingUp();
    }

    /** True while the incoming gun is still being drawn after a weapon swap (pullout or reload). */
    public boolean isSwapping() {
        return System.currentTimeMillis() < swapEndsAt;
    }

    @Override
    public boolean execute(Hero hero) {
        if (cooldown.isInUse() || isUltWindingUp(hero)) {
            return false;
        }
        BaseAbility gun = activeGun();
        if (unlimitedAmmo && gun.getAmmo() != null) {
            gun.getAmmo().setAmmo(gun.getAmmo().getMax());
        }
        boolean fired = gun.execute(hero);
        if (fired) {
            ticksSinceLastShot = 0;
            if (!penaltyDisabled && !penaltyActive) {
                penaltyActive = true;
                hero.getAttributes().setSpeedMultiplier(hero.getController(), movementPenaltyMultiplier);
            }
        }
        return fired;
    }

    @Override
    public void perform(Hero hero) { }

    void onModeSwapped(Hero hero) {
        Ammo incoming = activeGun().getAmmo();
        int drawTicks = incoming.getCurrent() < incoming.getMax() ? swapReloadTicks : pulloutTicks;
        incoming.refill();
        cooldown.start(drawTicks * 50L);
        swapEndsAt = System.currentTimeMillis() + drawTicks * 50L;
    }

    void drainClips(Hero hero) {
        minigun.getAmmo().drain();
        shotgun.getAmmo().drain();
        Ammo active = activeGun().getAmmo();
        if (active.getReloadSound() != null) active.getReloadSound().play(hero.getController());
    }

    @Override
    public String getActiveStatusText() {
        return "Swapping";
    }

    @Override
    protected void onTick(Hero hero) {
        minigun.tick(hero);
        shotgun.tick(hero);

        ticksSinceLastShot++;
        if (penaltyActive && ticksSinceLastShot > penaltyLingerTicks) {
            penaltyActive = false;
            hero.getAttributes().setSpeedMultiplier(hero.getController(), 1.0);
        }
    }

    public void setUnlimitedAmmo(boolean unlimited) {
        this.unlimitedAmmo = unlimited;
    }

    public void setPenaltyDisabled(Hero hero, boolean disabled) {
        this.penaltyDisabled = disabled;
        if (disabled && penaltyActive) {
            penaltyActive = false;
            hero.getAttributes().setSpeedMultiplier(hero.getController(), 1.0);
        }
    }

    public void applyFireRateMultiplier(double multiplier) {
        minigun.getCooldownObject().setCooldownMultiplier(multiplier);
        shotgun.getCooldownObject().setCooldownMultiplier(multiplier);
    }

    @Override
    public boolean isIndependent() { return true; }

    @Override
    public void onOwnerLost(Hero hero) {
        shotgun.onOwnerLost(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        minigun.onDeath(hero);
        shotgun.onDeath(hero);
        swapEndsAt = 0;
        if (penaltyActive) {
            penaltyActive = false;
            if (hero.getController() != null && hero.getController().getEntity() != null) {
                hero.getAttributes().setSpeedMultiplier(hero.getController(), 1.0);
            }
        }
    }

    private static class MinigunMode extends Hitscan {
        private final double kbFalloffStart;
        private final double kbFalloffEnd;
        private final double kbFalloffMinMult;

        MinigunMode(ConfigurationSection config, HeroSkin skin) {
            super(config, skin);
            this.kbFalloffStart = Configs.requireDouble(this.config, "kb_falloff_start");
            this.kbFalloffEnd = Configs.requireDouble(this.config, "kb_falloff_end");
            this.kbFalloffMinMult = Configs.requireDouble(this.config, "kb_falloff_min_mult");
        }

        @Override
        protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
            Interactions.on(attacker.getController().getEntity(), victim)
                    .abilityName(getName())
                    .damage(calcDamage(attacker, victim))
                    .knockback(calcKnockback(attacker, victim))
                    .direction(currentDirection)
                    .accumulate()
                    .knockbackFalloff(kbFalloffStart, kbFalloffEnd, kbFalloffMinMult)
                    .execute();
        }
    }

    /** One cone hit per shot; the pellets are visual only. */
    private static class ShotgunMode extends Cone {
        private final int pelletCount;
        private final PelletVisuals pellets;
        private final double dmgLengthMinMult, dmgLengthMaxMult, dmgAccuracyMinMult, dmgAccuracyMaxMult;
        private final double kbLengthMinMult, kbLengthMaxMult, kbAccuracyMinMult, kbAccuracyMaxMult;

        ShotgunMode(ConfigurationSection config, HeroSkin skin) {
            super(config, skin);
            this.pelletCount = Configs.requireInt(this.config, "pellet_count");
            double maxAngle = Configs.requireDouble(this.config, "max_angle");
            this.range = Configs.requireDouble(this.config, "range");
            this.minRadius = Configs.requireDouble(this.config, "min_radius");
            this.radius = minRadius + range * Math.tan(Math.toRadians(maxAngle));
            this.pellets = new PelletVisuals(this.config, skin, maxAngle);

            this.dmgLengthMinMult = Configs.requireDouble(this.config, "dmg_length_min_mult");
            this.dmgLengthMaxMult = Configs.requireDouble(this.config, "dmg_length_max_mult");
            this.dmgAccuracyMinMult = Configs.requireDouble(this.config, "dmg_accuracy_min_mult");
            this.dmgAccuracyMaxMult = Configs.requireDouble(this.config, "dmg_accuracy_max_mult");
            this.kbLengthMinMult = Configs.requireDouble(this.config, "kb_length_min_mult");
            this.kbLengthMaxMult = Configs.requireDouble(this.config, "kb_length_max_mult");
            this.kbAccuracyMinMult = Configs.requireDouble(this.config, "kb_accuracy_min_mult");
            this.kbAccuracyMaxMult = Configs.requireDouble(this.config, "kb_accuracy_max_mult");
        }

        @Override
        protected void displayVisuals(Hero hero, Location start, Vector direction) {
            for (int i = 0; i < pelletCount; i++) pellets.fire(hero);
        }

        @Override
        protected void onHit(Hero hero, LivingEntity victim, Vector direction) {
            Vector toVictim = victim.getBoundingBox().getCenter().subtract(hero.getController().getEyeLocation().toVector());
            double along = toVictim.dot(direction);
            double length = Math.min(Math.max(along, 0), range);
            double offset = toVictim.subtract(direction.clone().multiply(along)).length();
            double lengthT = length / range;
            double accuracyT = Math.min(offset / (minRadius + lengthT * (radius - minRadius)), 1);

            double dmg = damage * lerp(dmgLengthMinMult, dmgLengthMaxMult, lengthT) * lerp(dmgAccuracyMinMult, dmgAccuracyMaxMult, accuracyT);
            double kb = knockback * lerp(kbLengthMinMult, kbLengthMaxMult, lengthT) * lerp(kbAccuracyMinMult, kbAccuracyMaxMult, accuracyT);

            hitSound.play(victim.getEyeLocation(), hero.getController());
            hitVisual.play(victim.getEyeLocation());
            hero.triggerAbilityHit(this, victim);
            Interactions.on(hero.getController().getEntity(), victim)
                    .abilityName(getName()).damage(dmg).knockback(kb).direction(direction).execute();
        }

        private static double lerp(double from, double to, double t) {
            return from + (to - from) * t;
        }

        @Override
        protected void onTick(Hero hero) {
            super.onTick(hero);
            pellets.tick(hero);
        }

        @Override
        public boolean isIndependent() { return true; }

        @Override
        public void onOwnerLost(Hero hero) {
            pellets.onOwnerLost(hero);
        }
    }

    /** Flies the pellet model downrange for show: it never damages anyone and passes through players. */
    private static class PelletVisuals extends Projectile {
        private final double maxAngle;

        PelletVisuals(ConfigurationSection config, HeroSkin skin, double maxAngle) {
            super(config, skin);
            this.maxAngle = maxAngle;
            this.piercesPlayers = true;
        }

        void fire(Hero hero) {
            fireSingleShot(hero);
        }

        @Override
        public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) { }

        @Override
        protected Vector getDirection(Location location) {
            return VectorUtil.randomSpread(location.getDirection(), maxAngle);
        }
    }
}
