package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.ui.visual.ProgressBar;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GreenHoodBow extends BaseAbility {
    private static final class TrackedArrow {
        final AbstractArrow arrow;
        final ArrowShot shot;
        Location prevLoc;

        TrackedArrow(AbstractArrow arrow, ArrowShot shot) {
            this.arrow = arrow;
            this.shot = shot;
            this.prevLoc = arrow.getLocation();
        }
    }

    private final double speedMax;
    private final double hitRadius;

    private final double minDamage, maxDamage;
    private final double normalMinForce, normalForceMultiplier;
    private final double overchargeDamageMultiplier;
    private final int maxOverchargeTicks;

    private final SoundEffect hitSound;
    private final Map<UUID, TrackedArrow> activeArrows = new HashMap<>();

    public GreenHoodBow(ConfigurationSection config, HeroSkin skin) {
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

        this.hitSound = new SoundEffect(Configs.requireSection(this.config, "visuals.hit_sound"));
    }

    public static double overchargeDamage(double minDamage, double maxDamage, double charge,
                                          int overchargeTicks, int maxOverchargeTicks, double overchargeMult) {
        double overchargeFraction = maxOverchargeTicks > 0
                ? Math.min(1.0, overchargeTicks / (double) maxOverchargeTicks) : 0;
        double factor = 1.0 + overchargeFraction * (overchargeMult - 1.0);
        return (minDamage + (maxDamage - minDamage) * charge) * factor;
    }

    public double launchSpeed(double force, double minForce, double forceMultiplier) {
        return Math.max(minForce, force) * speedMax * forceMultiplier;
    }

    public void launch(AbstractArrow arrow, Vector aim, double speed, Vector carried, ArrowShot shot) {
        arrow.setVelocity(aim.clone().normalize().multiply(speed).add(carried));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        activeArrows.put(arrow.getUniqueId(), new TrackedArrow(arrow, shot));
    }

    public boolean ownsArrow(UUID arrowId) {
        return activeArrows.containsKey(arrowId);
    }

    @Override
    public void perform(Hero hero) {
    }

    public void handleShoot(Hero hero, EntityShootBowEvent event, Vector aim, Vector carried,
                            double charge, int overchargeTicks, double force) {
        AbstractArrow arrow = (AbstractArrow) event.getProjectile();
        for (Ability ability : hero.getAbilities()) {
            if (ability instanceof ArmedShot armed && armed.isArmed()) {
                if (!armed.fireFromBow(hero, this, arrow, aim, carried, charge, overchargeTicks, force)) {
                    event.setCancelled(true);
                }
                return;
            }
        }

        double dmg = overchargeDamage(minDamage, maxDamage, charge, overchargeTicks, maxOverchargeTicks, overchargeDamageMultiplier);
        double speed = launchSpeed(force, normalMinForce, normalForceMultiplier);
        launch(arrow, aim, speed, carried, new ArrowShot() {
            @Override
            public void onEntityHit(Hero h, LivingEntity victim, AbstractArrow a) {
                hitSound.play(victim.getEyeLocation(), h.getController());
                hit(h, victim).damage(dmg).knockback(knockback).direction(a.getVelocity()).execute();
                a.remove();
            }

            @Override
            public void onGroundHit(Hero h, AbstractArrow a) {
                a.remove();
            }
        });
    }

    public void handleEntityHit(Hero hero, AbstractArrow arrow, LivingEntity victim) {
        TrackedArrow tracked = activeArrows.remove(arrow.getUniqueId());
        if (tracked != null) {
            if (!PracticeManager.isWithinPracticeBounds(hero, arrow.getLocation())
                    || !PracticeManager.isWithinPracticeBounds(hero, victim.getLocation())) {
                arrow.remove();
                return;
            }
            tracked.shot.onEntityHit(hero, victim, arrow);
        }
    }

    public void handleGroundHit(Hero hero, AbstractArrow arrow) {
        TrackedArrow tracked = activeArrows.remove(arrow.getUniqueId());
        if (tracked != null) {
            if (!PracticeManager.isWithinPracticeBounds(hero, arrow.getLocation())) {
                arrow.remove();
                return;
            }
            tracked.shot.onGroundHit(hero, arrow);
        }
    }

    @Override
    public boolean isIndependent() {
        return true;
    }

    @Override
    protected void onTick(Hero hero) {
        LivingEntity shooter = hero.getController() != null ? hero.getController().getEntity() : null;
        for (TrackedArrow tracked : new ArrayList<>(activeArrows.values())) {
            AbstractArrow arrow = tracked.arrow;
            if (!arrow.isValid() || shooter == null) {
                activeArrows.remove(arrow.getUniqueId());
                continue;
            }
            Location current = arrow.getLocation();
            if (!PracticeManager.isWithinPracticeBounds(hero, current)) {
                activeArrows.remove(arrow.getUniqueId());
                arrow.remove();
                continue;
            }
            Vector delta = current.toVector().subtract(tracked.prevLoc.toVector());
            if (delta.lengthSquared() < 0.0001) {
                tracked.prevLoc = current;
                continue;
            }
            // Vanilla arrow has already resolved blocks; sweep only entities along its actual step.
            Set<LivingEntity> victims = Hitbox.box(tracked.prevLoc, delta, hitRadius, hitRadius, delta.length())
                    .exclude(shooter)
                    .excludeTeammates(shooter)
                    .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                    .pierce(false)
                    .get();
            tracked.prevLoc = current;

            if (!victims.isEmpty()) {
                activeArrows.remove(arrow.getUniqueId());
                tracked.shot.onEntityHit(hero, victims.iterator().next(), arrow);
            }
        }

        if (!hero.isEliminated() && hero.getController().isDrawingBow()) {
            long heldMs = GreenHoodListener.drawHeldMs(hero.getController().getUniqueId());
            if (heldMs > GreenHoodListener.FULL_DRAW_MS) {
                double fraction = maxOverchargeTicks > 0
                        ? Math.min(1.0, (heldMs - GreenHoodListener.FULL_DRAW_MS) / (maxOverchargeTicks * 50.0)) : 0;
                hero.getController().sendActionBar(ProgressBar.actionBar("Overcharge", NamedTextColor.GOLD, fraction));
            }
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        for (TrackedArrow tracked : activeArrows.values()) {
            if (tracked.arrow.isValid()) tracked.arrow.remove();
        }
        activeArrows.clear();
    }
}
