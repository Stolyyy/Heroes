package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.listener.GreenHoodListener;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.VectorUtil;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class Arrow extends BaseAbility {
    private final double speedMax;
    private final double hitRadius;

    private final double minDamage, maxDamage;
    private final double normalMinForce, normalForceMultiplier;
    private final double overchargeDamageMultiplier;
    private final int maxOverchargeTicks;

    private final double barrageDamage, barrageKnockback, barrageMaxAngle, barrageHitRadius;
    private final double barrageMinForce, barrageForceMultiplier;
    private final int baseArrows, ticksPerExtraArrow, maxArrows, shotIntervalTicks;

    private final SoundEffect fireSound;
    private final SoundEffect hitSound;

    private GreenHoodSwap swap;

    private int queuedArrows;
    private double queuedSpeed;
    private Vector queuedCarried;
    private int ticksUntilNextShot;

    public Arrow(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speedMax = Configs.requireDouble(this.config, "speed_max");
        this.hitRadius = Configs.requireDouble(this.config, "hit_radius");

        ConfigurationSection normal = Configs.requireSection(this.config, "normal");
        this.minDamage = Configs.requireDouble(normal, "min_damage");
        this.maxDamage = Configs.requireDouble(normal, "max_damage");
        this.normalMinForce = Configs.requireDouble(normal, "min_force");
        this.normalForceMultiplier = Configs.requireDouble(normal, "force_multiplier");
        this.overchargeDamageMultiplier = Configs.requireDouble(normal, "overcharge_damage_multiplier");
        this.maxOverchargeTicks = Configs.requireInt(normal, "max_overcharge_ticks");

        ConfigurationSection barrage = Configs.requireSection(this.config, "barrage");
        this.barrageDamage = Configs.requireDouble(barrage, "damage");
        this.barrageKnockback = Configs.requireDouble(barrage, "knockback");
        this.barrageMinForce = Configs.requireDouble(barrage, "min_force");
        this.barrageForceMultiplier = Configs.requireDouble(barrage, "force_multiplier");
        this.baseArrows = Configs.requireInt(barrage, "base_arrows");
        this.ticksPerExtraArrow = Configs.requireInt(barrage, "ticks_per_extra_arrow");
        this.maxArrows = Configs.requireInt(barrage, "max_arrows");
        this.barrageMaxAngle = Configs.requireDouble(barrage, "max_angle");
        this.barrageHitRadius = Configs.requireDouble(barrage, "hit_radius");
        this.shotIntervalTicks = Configs.requireInt(barrage, "shot_interval_ticks");

        this.fireSound = new SoundEffect(this.config.getConfigurationSection("visuals.fire_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
    }

    public double launchSpeed(double force, double minForce, double forceMultiplier) {
        return Math.max(minForce, force) * speedMax * forceMultiplier;
    }

    public double hitRadius() { return hitRadius; }
    public double barrageHitRadius() { return barrageHitRadius; }

    @Override
    public void initialize(Hero hero) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive instanceof GreenHoodSwap s) {
                this.swap = s;
                return;
            }
        }
    }

    @Override
    public void perform(Hero hero) {
    }

    /**
     * @param arrow   the bow's own arrow from the shoot event, not yet in the world
     * @param aim     the shooter's look direction
     * @param carried the non-aim part of vanilla's launch velocity (momentum + spread), see
     *                {@link GreenHoodListener#onShoot}
     */
    public void shoot(Hero hero, AbstractArrow arrow, Vector aim, Vector carried,
                      double charge, int overchargeTicks, double force) {
        double overchargeFraction = maxOverchargeTicks > 0
                ? Math.min(1.0, overchargeTicks / (double) maxOverchargeTicks) : 0;

        if (swap != null && swap.getMode() == GreenHoodSwap.FireMode.BARRAGE) {
            double speed = launchSpeed(force, barrageMinForce, barrageForceMultiplier);
            int count = Math.min(maxArrows,
                    baseArrows + (ticksPerExtraArrow > 0 ? overchargeTicks / ticksPerExtraArrow : 0));
            GreenHoodListener.aim(arrow, VectorUtil.randomSpread(aim, barrageMaxAngle), speed, carried,
                    "barrage", barrageDamage);
            queuedArrows = count - 1;
            queuedSpeed = speed;
            queuedCarried = carried.clone();
            ticksUntilNextShot = shotIntervalTicks;
        } else {
            double factor = 1.0 + overchargeFraction * (overchargeDamageMultiplier - 1.0);
            double dmg = (minDamage + (maxDamage - minDamage) * charge) * factor;
            double speed = launchSpeed(force, normalMinForce, normalForceMultiplier);
            GreenHoodListener.aim(arrow, aim, speed, carried, "normal", dmg);
        }
    }

    /**
     * Queued barrage arrows have no bow release behind them, so they are launched outright and
     * reuse the momentum the opening shot carried: the volley is a couple of ticks long and the
     * whole spread should fly as one.
     */
    private void fireQueuedBarrageArrow(Player shooter) {
        AbstractArrow arrow = shooter.launchProjectile(org.bukkit.entity.Arrow.class);
        arrow.setCritical(false);
        GreenHoodListener.aim(arrow, VectorUtil.randomSpread(shooter.getEyeLocation().getDirection(), barrageMaxAngle),
                queuedSpeed, queuedCarried, "barrage", barrageDamage);
    }

    public void onArrowHit(Hero hero, LivingEntity victim, AbstractArrow arrow) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hero.triggerAbilityHit(this, victim);
        boolean barrage = "barrage".equals(GreenHoodListener.readTag(arrow));
        Interactions.on(hero.getController().getEntity(), victim)
                .abilityName(getName())
                .damage(GreenHoodListener.readDamage(arrow))
                .knockback(barrage ? barrageKnockback : knockback)
                .direction(arrow.getVelocity())
                .execute();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        queuedArrows = 0;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        queuedArrows = 0;
    }

    @Override
    protected void onTick(Hero hero) {
        if (!(hero.getController().getEntity() instanceof Player p)) return;

        if (queuedArrows > 0) {
            ticksUntilNextShot--;
            if (ticksUntilNextShot <= 0) {
                fireSound.play(p.getLocation(), hero.getController());
                fireQueuedBarrageArrow(p);
                queuedArrows--;
                ticksUntilNextShot = shotIntervalTicks;
            }
        }

        if (!p.isHandRaised() || p.getInventory().getItemInMainHand().getType() != Material.BOW) return;
        long heldMs = GreenHoodListener.drawHeldMs(p);
        if (heldMs <= GreenHoodListener.FULL_DRAW_MS) return;

        double fraction = maxOverchargeTicks > 0
                ? Math.min(1.0, (heldMs - GreenHoodListener.FULL_DRAW_MS) / (maxOverchargeTicks * 50.0)) : 0;
        int bars = (int) Math.round(fraction * 10);
        String fill = "█".repeat(bars) + "░".repeat(10 - bars);
        hero.getController().sendActionBar(Component.text("Overcharge ", NamedTextColor.GOLD)
                .append(Component.text(fill, fraction >= 1.0 ? NamedTextColor.RED : NamedTextColor.YELLOW)));
    }
}
