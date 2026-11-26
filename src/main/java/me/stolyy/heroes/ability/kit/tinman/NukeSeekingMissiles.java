package me.stolyy.heroes.ability.kit.tinman;

import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Homing;
import me.stolyy.heroes.physics.Targeting;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.List;

public class NukeSeekingMissiles extends Projectile {
    private final int missilesPerEnemy;
    private final double launchHeight;
    private final int maxTicks;
    private final int maxMissiles;
    private final SoundEffect activationSound;

    private List<LivingEntity> salvoTargets = List.of();

    public NukeSeekingMissiles(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.missilesPerEnemy = Configs.requireInt(this.config, "missiles_per_enemy");
        this.launchHeight = Configs.requireDouble(this.config, "launch_height");
        this.maxTicks = Configs.requireInt(this.config, "max_ticks");
        this.maxMissiles = Configs.requireInt(this.config, "max_missiles");
        this.activationSound = new SoundEffect(Configs.requireSection(this.config, "visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        // Player enemies only for salvo count; summoners' minions shouldn't triple salvo size.
        salvoTargets = Targeting.enemies(hero.getController().getEntity(), false);
        super.perform(hero);
    }

    @Override
    protected int burstCountFor(Hero hero) {
        return Math.min(salvoTargets.size() * missilesPerEnemy, maxMissiles);
    }

    @Override
    protected ProjectileInstance createInstance(Hero hero, Location start, ShotParams shot) {
        LivingEntity target = salvoTargets.get(currentShotIndex() % salvoTargets.size());
        return new MissileInstance(hero, start, shot, target);
    }

    @Override
    public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
        detonateAoe(attacker, hitLoc, currentDirection, null);
    }

    private class MissileInstance extends ProjectileInstance {
        private final Homing homing;
        private boolean seeking = false;

        MissileInstance(Hero hero, Location start, ShotParams shot, LivingEntity target) {
            super(hero, start, new Vector(0, 1, 0), shot);
            LivingEntity self = hero.getController().getEntity();
            this.homing = new Homing(
                    target,
                    shot.speed(),
                    () -> Targeting.nearestEnemy(self, currentLoc, true),
                    victim -> GameManager.canTarget(self, victim)
            );
        }

        @Override
        protected boolean steer() {
            if (ticksAlive > maxTicks) return false;
            Vector homingVel = homing.step(currentLoc);
            if (homingVel == null) return false;

            if (seeking) {
                velocity.copy(homingVel);
            } else if (distanceTraveled >= launchHeight) {
                seeking = true;
            }
            return true;
        }
    }
}
