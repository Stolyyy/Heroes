package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.CompositeAbility;
import me.stolyy.heroes.ability.template.Cone;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

public class PunishgersWeapon extends CompositeAbility {
    public enum WeaponMode { MINIGUN, SHOTGUN }

    private final MinigunMode minigun;
    private final ShotgunMode shotgun;
    private final double movementPenaltyMultiplier;
    private final int penaltyLingerTicks;
    private final int pulloutTicks;
    private final int swapReloadTicks;

    private WeaponMode mode = WeaponMode.MINIGUN;
    private boolean penaltyActive;
    private int ticksSinceLastShot;

    private boolean unlimitedAmmo;
    private boolean penaltyDisabled;

    public PunishgersWeapon(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.minigun = addChild(new MinigunMode(Configs.requireSection(this.config, "minigun"), skin));
        this.shotgun = addChild(new ShotgunMode(Configs.requireSection(this.config, "shotgun"), skin));
        this.movementPenaltyMultiplier = Configs.requireDouble(this.config, "movement_penalty_multiplier");
        this.penaltyLingerTicks = Configs.requireInt(this.config, "penalty_linger_ticks");
        this.pulloutTicks = Configs.requireInt(this.config, "pullout_ticks");
        this.swapReloadTicks = Configs.requireInt(this.config, "swap_reload_ticks");
    }

    public boolean isShotgun() {
        return mode == WeaponMode.SHOTGUN;
    }

    private BaseAbility activeGun() {
        return isShotgun() ? shotgun : minigun;
    }

    @Override
    public Ammo getAmmo() {
        return activeGun().getAmmo();
    }

    @Override
    public Cooldown getDisplayCooldown(Hero hero) {
        return isSwapping() ? cooldown : activeGun().getCooldownObject();
    }

    @Override
    protected boolean canUse(Hero hero) {
        if (isSwapping() || isUltWindingUp(hero)) return false;
        return activeGun().canExecute(hero);
    }

    private static boolean isUltWindingUp(Hero hero) {
        return hero.getSmashAbility() instanceof BaseAbility ult && ult.isWindingUp();
    }

    /** True while the incoming gun is still being drawn after a weapon swap (cooldown active window). */
    public boolean isSwapping() {
        return cooldown.isInUse();
    }

    @Override
    public void perform(Hero hero) {
        BaseAbility gun = activeGun();
        if (unlimitedAmmo && gun.getAmmo() != null) {
            gun.getAmmo().setAmmo(gun.getAmmo().getMax());
        }
        if (gun.execute(hero)) {
            ticksSinceLastShot = 0;
            if (!penaltyDisabled && !penaltyActive) {
                penaltyActive = true;
                hero.getAttributes().addSpeedMultiplier(hero.getController(), this, movementPenaltyMultiplier);
            }
        }
    }

    void swap(Hero hero) {
        mode = isShotgun() ? WeaponMode.MINIGUN : WeaponMode.SHOTGUN;
        Ammo incoming = activeGun().getAmmo();
        int drawTicks = incoming.getCurrent() < incoming.getMax() ? swapReloadTicks : pulloutTicks;
        incoming.refill();
        cooldown.start(drawTicks * Heroes.MS_PER_TICK);
        CosmeticManager.setHeldModel(hero, isShotgun()
                ? "shotgun" : CosmeticManager.defaultHeldTexture(hero));
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
    protected void onCompositeTick(Hero hero) {
        ticksSinceLastShot++;
        if (penaltyActive && ticksSinceLastShot > penaltyLingerTicks) {
            penaltyActive = false;
            hero.getAttributes().removeSpeedModifier(hero.getController(), this);
        }
    }

    public void setUnlimitedAmmo(boolean unlimited) {
        this.unlimitedAmmo = unlimited;
        if (unlimited) {
            minigun.getAmmo().refill();
            shotgun.getAmmo().refill();
        }
    }

    public void setPenaltyDisabled(Hero hero, boolean disabled) {
        this.penaltyDisabled = disabled;
        if (disabled && penaltyActive) {
            penaltyActive = false;
            hero.getAttributes().removeSpeedModifier(hero.getController(), this);
        }
    }

    public void addFireRateModifier(Object key, double multiplier) {
        minigun.getCooldownObject().addDurationMultiplier(key, multiplier);
        shotgun.getCooldownObject().addDurationMultiplier(key, multiplier);
    }

    public void removeFireRateModifier(Object key) {
        minigun.getCooldownObject().removeDurationModifier(key);
        shotgun.getCooldownObject().removeDurationModifier(key);
    }

    @Override
    protected void onCompositeDeath(Hero hero) {
        mode = WeaponMode.MINIGUN;
        if (penaltyActive) {
            penaltyActive = false;
            hero.getAttributes().removeSpeedModifier(hero.getController(), this);
        }
    }

    @Override
    protected void onCompositeOwnerLost(Hero hero) {
        if (penaltyActive) {
            penaltyActive = false;
            hero.getAttributes().removeSpeedModifier(hero.getController(), this);
        }
    }

    private static class MinigunMode extends Hitscan {
        private final double kbFalloffStart;
        private final double kbFalloffEnd;
        private final double kbFalloffMinMult;

        MinigunMode(ConfigurationSection config, HeroSkin skin) {
            super(config, skin, AbilityType.PRIMARY);
            this.kbFalloffStart = Configs.requireDouble(this.config, "kb_falloff_start");
            this.kbFalloffEnd = Configs.requireDouble(this.config, "kb_falloff_end");
            this.kbFalloffMinMult = Configs.requireDouble(this.config, "kb_falloff_min_mult");
        }

        @Override
        protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
            hit(attacker, victim)
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
            super(config, skin, AbilityType.PRIMARY);
            this.pelletCount = Configs.requireInt(this.config, "pellet_count");
            double maxAngle = Configs.requireDouble(this.config, "spread_deg");
            this.range = Configs.requireDouble(this.config, "range");
            this.minRadius = Configs.requireDouble(this.config, "min_radius");
            this.radius = minRadius + range * Math.tan(Math.toRadians(maxAngle));
            this.pellets = new PelletVisuals(this.config, skin);

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
            hit(hero, victim).damage(dmg).knockback(kb).direction(direction).execute();
        }

        private static double lerp(double from, double to, double t) {
            return from + (to - from) * t;
        }

        @Override
        protected void onConeTick(Hero hero) {
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
        PelletVisuals(ConfigurationSection config, HeroSkin skin) {
            super(config, skin, AbilityType.PRIMARY);
            this.piercesPlayers = true;
        }

        void fire(Hero hero) {
            fireSingleShot(hero);
        }

        @Override
        public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) { }
    }
}
