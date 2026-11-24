package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.Pull;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class ChainHook extends Projectile {
    private enum PullMode { NONE, LOCATION, PLAYER }

    private final double pullStart;
    private final double pullAccelPerTick;
    private final double pullMax;
    private final double pullSpeedTarget;
    private final int maxPullTicks;
    private final int maxPullTicksTarget;
    private final int energyCostPerTick;
    private final double driftStrength;
    private final double verticalDriftStrength;
    private final Component touchGroundMessage;

    private PullMode mode = PullMode.NONE;
    private Location pullLocation;
    private LivingEntity pullTarget;
    private int pullTicksElapsed;

    private boolean needsGroundContact;

    public ChainHook(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.pullStart = Configs.requireDouble(this.config, "pull_start");
        this.pullAccelPerTick = Configs.requireDouble(this.config, "pull_accel_per_tick");
        this.pullMax = Configs.requireDouble(this.config, "pull_max");
        this.pullSpeedTarget = Configs.requireDouble(this.config, "pull_speed_target");
        this.maxPullTicks = Configs.requireInt(this.config, "max_pull_ticks");
        this.maxPullTicksTarget = Configs.requireInt(this.config, "max_pull_ticks_target");
        this.energyCostPerTick = Configs.requireInt(this.config, "energy_cost_per_tick");
        this.driftStrength = Configs.requireDouble(this.config, "drift_strength");
        this.verticalDriftStrength = Configs.requireDouble(this.config, "vertical_drift_strength");
        this.touchGroundMessage = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(Configs.requireString(this.config, "messages.touch_ground"));
    }

    @Override
    protected boolean canUse(Hero hero) {
        if (!activeProjectiles.isEmpty() || mode != PullMode.NONE) return false;
        if (needsGroundContact) {
            hero.getController().sendMessage(touchGroundMessage);
            return false;
        }
        return true;
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        resolveCooldown(attacker);
        hitSound.play(victim.getEyeLocation(), attacker.getController());
        hitVisual.play(victim.getEyeLocation());
        if (damage > 0) {
            hit(attacker, victim).damage(damage).execute();
        } else {
            attacker.triggerAbilityHit(this, victim);
        }
        pullTarget = victim;
        mode = PullMode.PLAYER;
        pullTicksElapsed = 0;
        needsGroundContact = true;
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        resolveCooldown(hero);
        wallSound.play(location, hero.getController());
        wallVisual.play(location);
        pullLocation = location.clone();
        mode = PullMode.LOCATION;
        pullTicksElapsed = 0;
        needsGroundContact = true;
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        resolveMissCooldown();
    }

    @Override
    protected void onProjectileTick(Hero hero) {
        if (needsGroundContact && hero.getController().isOnGround()) needsGroundContact = false;

        if (mode == PullMode.NONE) return;

        int tickCap = mode == PullMode.PLAYER ? maxPullTicksTarget : maxPullTicks;

        if (!hero.getController().isSneaking()
                || ++pullTicksElapsed > tickCap
                || !hero.getEnergy().tryConsume(hero.getController(), energyCostPerTick)) {
            cancelPull();
            return;
        }

        if (mode == PullMode.PLAYER && (pullTarget == null || pullTarget.isDead() || !pullTarget.isValid())) {
            cancelPull();
            return;
        }
        Location anchor = mode == PullMode.PLAYER ? pullTarget.getLocation() : pullLocation;
        double arrivalDist = mode == PullMode.PLAYER ? 2.0 : 1.5;
        double pullSpeedSelf = Math.min(pullMax, pullStart + pullAccelPerTick * pullTicksElapsed);
        if (Pull.toward(hero.getController(), anchor, pullSpeedSelf, arrivalDist, driftVector(hero))) {
            cancelPull();
            return;
        }
        if (mode == PullMode.PLAYER) {
            Vector dir = anchor.toVector().subtract(hero.getController().getLocation().toVector()).normalize();
            Interactions.on(hero.getController().getEntity(), pullTarget)
                    .abilityName(getName())
                    .knockback(pullSpeedTarget * Interactions.KB_DIVISOR)
                    .direction(dir.multiply(-1))
                    .staticKnockback()
                    .processDirection(false)
                    .execute();
        }
    }

    private Vector driftVector(Hero hero) {
        Vector axes = hero.getController().getMovementAxes();
        double side = axes.getX();
        double lift = axes.getZ();
        if (side == 0 && lift == 0) return new Vector();
        double yaw = Math.toRadians(hero.getController().getLocation().getYaw());

        Vector drift = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw)).multiply(side * driftStrength);
        return drift.setY(lift * verticalDriftStrength);
    }

    private void cancelPull() {
        mode = PullMode.NONE;
        pullLocation = null;
        pullTarget = null;
        pullTicksElapsed = 0;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        for (ProjectileInstance p : new ArrayList<>(activeProjectiles)) {
            p.cleanup();
        }
        activeProjectiles.clear();
        needsGroundContact = false;
        cancelPull();
    }
}
