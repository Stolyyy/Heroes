package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

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
    private final long missCooldownMs;
    private final Component touchGroundMessage;

    private PullMode mode = PullMode.NONE;
    private Location pullLocation;
    private LivingEntity pullTarget;
    private int pullTicksElapsed;
    private boolean chainInFlight;

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
        this.missCooldownMs = (long) (Configs.requireDouble(this.config, "miss_cooldown") * 1000);
        this.touchGroundMessage = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(Configs.requireString(this.config, "messages.touch_ground"));
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (chainInFlight || mode != PullMode.NONE) return false;
        if (needsGroundContact) {
            hero.getController().sendMessage(touchGroundMessage);
            return false;
        }
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        cancelPull();
        chainInFlight = true;
        super.perform(hero);
    }


    @Override
    protected void startCooldown(Hero hero) { }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        chainInFlight = false;
        cooldown.start();
        hitSound.play(victim.getEyeLocation(), attacker.getController());
        hitVisual.play(victim.getEyeLocation());
        attacker.triggerAbilityHit(this, victim);
        if (damage > 0) {
            Interactions.on(attacker.getController().getEntity(), victim)
                    .abilityName(getName()).damage(damage).execute();
        }
        pullTarget = victim;
        mode = PullMode.PLAYER;
        pullTicksElapsed = 0;
        needsGroundContact = true;
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        chainInFlight = false;
        cooldown.start();
        wallSound.play(location, hero.getController());
        wallVisual.play(location);
        pullLocation = location.clone();
        mode = PullMode.LOCATION;
        pullTicksElapsed = 0;
        needsGroundContact = true;
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        chainInFlight = false;
        cooldown.start();
        cooldown.reduceCooldown(Math.max(0, cooldown.getCooldownDuration() - missCooldownMs));
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);
        if (needsGroundContact && hero.getController().isOnGround()) needsGroundContact = false;

        if (mode == PullMode.NONE) return;

        int tickCap = mode == PullMode.PLAYER ? maxPullTicksTarget : maxPullTicks;

        if (!hero.getController().isSneaking()
                || ++pullTicksElapsed > tickCap
                || !hero.getEnergy().tryConsume(hero.getController(), energyCostPerTick)) {
            cancelPull();
            return;
        }

        LivingEntity self = hero.getController().getEntity();
        double pullSpeedSelf = Math.min(pullMax, pullStart + pullAccelPerTick * pullTicksElapsed);
        if (mode == PullMode.LOCATION) {
            Vector diff = pullLocation.toVector().subtract(self.getLocation().toVector());
            if (diff.length() < 1.5) {
                cancelPull();
                return;
            }
            self.setVelocity(diff.normalize().multiply(pullSpeedSelf).add(driftVector(hero)));
        } else {
            if (pullTarget == null || pullTarget.isDead() || !pullTarget.isValid()) {
                cancelPull();
                return;
            }
            Vector diff = pullTarget.getLocation().toVector().subtract(self.getLocation().toVector());
            if (diff.length() < 2.0) {
                cancelPull();
                return;
            }

            Vector dir = diff.clone().normalize();
            self.setVelocity(dir.clone().multiply(pullSpeedSelf).add(driftVector(hero)));
            pullTarget.setVelocity(dir.clone().multiply(-pullSpeedTarget));
        }
    }

    private Vector driftVector(Hero hero) {
        if (!(hero.getController() instanceof PlayerController pc)) return new Vector();
        org.bukkit.Input input = pc.getPlayer().getCurrentInput();
        int side = (input.isRight() ? 1 : 0) - (input.isLeft() ? 1 : 0);
        int lift = (input.isForward() ? 1 : 0) - (input.isBackward() ? 1 : 0);
        if (side == 0 && lift == 0) return new Vector();
        double yaw = Math.toRadians(pc.getPlayer().getLocation().getYaw());

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
        chainInFlight = false;
        needsGroundContact = false;
        cancelPull();
    }
}
