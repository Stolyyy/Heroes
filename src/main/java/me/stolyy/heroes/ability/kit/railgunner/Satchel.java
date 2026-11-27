package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.ability.template.Planted;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class Satchel extends Projectile {
    private final double minVelocity;
    private final double maxVelocity;
    private final long stickyDuration;
    private final double explosionRadius;
    private final int sneakPopDelayTicks;

    private Planted planted;
    private long plantedAtMs;

    private boolean sneakThrown;
    private int ticksSinceThrow;

    private final SoundEffect popSound;

    public Satchel(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.popSound = new SoundEffect(Configs.requireSection(this.config, "visuals.pop_sound"));
        this.minVelocity = Configs.requireDouble(this.config, "satchel.min_velocity");
        this.maxVelocity = Configs.requireDouble(this.config, "satchel.max_velocity");
        this.stickyDuration = (long) (Configs.requireDouble(this.config, "satchel.duration") * 1000);
        this.explosionRadius = Configs.requireDouble(this.config, "satchel.radius");
        this.sneakPopDelayTicks = Configs.requireInt(this.config, "sneak_pop_delay_ticks");
    }

    private boolean hasLive() {
        return planted != null || !activeProjectiles.isEmpty();
    }

    private boolean popLive(Hero hero) {
        if (planted != null) {
            detonate(hero);
            return true;
        }
        if (!activeProjectiles.isEmpty()) {
            ProjectileInstance proj = activeProjectiles.getFirst();
            detonateAt(hero, proj.getLocation());
            proj.cleanup();
            activeProjectiles.clear();
            return true;
        }
        return false;
    }

    @Override
    protected boolean canRecast(Hero hero) {
        return hasLive();
    }

    @Override
    protected boolean tryRecast(Hero hero) {
        sneakThrown = false;
        return popLive(hero);
    }

    @Override
    public void perform(Hero hero) {
        sneakThrown = hero.getController().isSneaking();
        ticksSinceThrow = 0;
        super.perform(hero);
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        plant(attacker, hitLoc, victim);
    }

    @Override
    public void onHitWall(Hero hero, Location location) {
        plant(hero, location, null);
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        detonateAt(hero, location);
    }

    @Override
    protected void onProjectileTick(Hero hero) {
        if (planted != null) {
            planted.sync();
            if (!planted.isWithin(hero)) {
                planted.cleanup();
                planted = null;
                return;
            }
            if (System.currentTimeMillis() - plantedAtMs > stickyDuration) {
                detonate(hero);
            }
        }

        if (sneakThrown && ++ticksSinceThrow >= sneakPopDelayTicks) {
            sneakThrown = false;
            popLive(hero);
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        sneakThrown = false;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        sneakThrown = false;
        if (planted != null) {
            planted.cleanup();
            planted = null;
        }
    }

    private void plant(Hero hero, Location loc, @Nullable LivingEntity attached) {
        if (!PracticeManager.isWithinPracticeBounds(hero, loc)) return;
        ItemDisplay display = projVisual.spawnDisplay(loc, hero.getSkin().getId());
        planted = new Planted(loc, attached, display);
        plantedAtMs = System.currentTimeMillis();
        hitSound.play(hero.getController().getLocation(), hero.getController());
    }

    private void detonate(Hero hero) {
        if (planted == null) return;
        detonateAt(hero, planted.location());
        planted.cleanup();
        planted = null;
    }

    private void detonateAt(Hero hero, Location center) {
        if (!PracticeManager.isWithinPracticeBounds(hero, center)) return;
        popSound.play(center);
        hitVisual.play(center);
        hitSound.play(hero.getController().getLocation(), hero.getController());

        Hitbox.sphere(center, explosionRadius)
                .filter(victim -> PracticeManager.isWithinPracticeBounds(hero, victim.getLocation()))
                .onHit((victim) -> {
                    double dist = victim.getLocation().distance(center);
                    double factor = 1.0 - Math.min(1.0, dist / explosionRadius);

                    boolean isEnemy = GameManager.canTarget(hero.getController().getEntity(), victim);

                    if (isEnemy) {
                        hit(hero, victim)
                                .damage(damage)
                                .knockback(knockback)
                                .fromLocation(center)
                                .execute();
                    } else {
                        double velocityStrength = minVelocity + ((maxVelocity - minVelocity) * factor);

                        Vector direction = victim.getEyeLocation().toVector().subtract(center.toVector());
                        if (direction.lengthSquared() < 0.0001) direction = new Vector(0, 1, 0);
                        direction.normalize();

                        Vector finalVel = direction.multiply(velocityStrength);

                        if (finalVel.getY() < 0.2) finalVel.setY(finalVel.getY() + 0.2);

                        Interactions.applyVelocity(victim, finalVel);
                    }
                })
                .check();
    }
}
