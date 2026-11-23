package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.effect.ParticleBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

public class PunishgersSecondary extends BaseAbility {
    private final GrenadeProjectile molotov;
    private final GrenadeProjectile stunGrenade;

    private final double dotDamage;
    private final double dotRadius;
    private final int dotDurationTicks;
    private final int dotHitIntervalTicks;
    private final AreaDisc molotovArea;
    private final SoundEffect dotHitSound;

    private final double stunRadius;
    private final int levitationAmplifier;
    private final long levitationMs;
    private final long disorientMs;
    private final PotionEffectType disorientEffect;
    private final AreaDisc stunArea;
    private final SoundEffect stunHitSound;

    private final List<DotZone> dotZones = new ArrayList<>();

    private PunishgersSwap swap;
    private PunishgersWeapon weapon;

    public PunishgersSecondary(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        ConfigurationSection molotovConfig = Configs.requireSection(this.config, "molotov");
        ConfigurationSection stunConfig = Configs.requireSection(this.config, "stun");
        this.molotov = new GrenadeProjectile(molotovConfig, skin, true);
        this.stunGrenade = new GrenadeProjectile(stunConfig, skin, false);

        this.dotDamage = Configs.requireDouble(molotovConfig, "dot_damage");
        this.dotRadius = Configs.requireDouble(molotovConfig, "dot_radius");
        this.dotDurationTicks = (int) (Configs.requireDouble(molotovConfig, "dot_duration") * 20);
        this.dotHitIntervalTicks = Configs.requireInt(molotovConfig, "dot_hit_interval");
        this.molotovArea = new AreaDisc(molotovConfig);
        this.dotHitSound = new SoundEffect(molotovConfig.getConfigurationSection("visuals.dot_hit_sound"));

        this.stunRadius = Configs.requireDouble(stunConfig, "effect_radius");
        this.levitationAmplifier = Configs.requireInt(stunConfig, "levitation_amplitude");
        this.levitationMs = Configs.requireLong(stunConfig, "levitation_ms");
        this.disorientMs = Configs.requireLong(stunConfig, "disorient_ms");
        this.disorientEffect = switch (Configs.requireString(stunConfig, "disorient_effect").toUpperCase()) {
            case "NAUSEA" -> PotionEffectType.NAUSEA;
            case "BLINDNESS" -> PotionEffectType.BLINDNESS;
            default -> throw new IllegalStateException("disorient_effect must be NAUSEA or BLINDNESS");
        };
        this.stunArea = new AreaDisc(stunConfig);
        this.stunHitSound = new SoundEffect(stunConfig.getConfigurationSection("visuals.effect_hit_sound"));
    }

    @Override
    public void initialize(Hero hero) {
        this.swap = PunishgersSwap.find(hero);
        this.weapon = (PunishgersWeapon) hero.getPrimaryAbility();
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (weapon.isSwapping()) return false;
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        boolean shotgunMode = swap != null && swap.getMode() == PunishgersSwap.WeaponMode.SHOTGUN;
        (shotgunMode ? stunGrenade : molotov).perform(hero);
    }

    private void onGrenadeLand(Hero hero, Location location, boolean isMolotov) {
        if (isMolotov) {
            dotZones.add(new DotZone(hero, location.clone(), dotDurationTicks));
        } else {
            detonateStun(hero, location);
        }
    }

    @Override
    protected void onTick(Hero hero) {
        molotov.tick(hero);
        stunGrenade.tick(hero);
        dotZones.removeIf(DotZone::tick);
    }

    @Override
    public boolean isIndependent() {
        return true;
    }

    @Override
    public void onOwnerLost(Hero hero) {
        molotov.onOwnerLost(hero);
        stunGrenade.onOwnerLost(hero);
        dotZones.clear();
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        molotov.onDeath(hero);
        stunGrenade.onDeath(hero);
    }

    private void detonateStun(Hero owner, Location center) {
        stunArea.play(center, stunRadius);
        LivingEntity self = owner.getController().getEntity();
        Hitbox.sphere(center, stunRadius)
                .exclude(self)
                .excludeTeammates(self)
                .onHit(victim -> {
                    stunHitSound.play(victim.getEyeLocation(), owner.getController());
                    owner.triggerAbilityHit(this, victim);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION,
                            (int) (levitationMs / 50), levitationAmplifier, false, false, false));
                    victim.addPotionEffect(new PotionEffect(disorientEffect,
                            (int) (disorientMs / 50), 0, false, false, false));
                    Hero victimHero = HeroManager.getHero(victim);
                    if (victimHero != null) victimHero.getMobility().root(levitationMs);
                })
                .check();
    }

    /** A filled particle disc for a grenade's area of effect, configured by the grenade's {@code area_*} keys. */
    private static class AreaDisc {
        private final ParticleBuilder particle;
        private final double ringSpacing;
        private final int pointsPerBlock;
        private final double jitter;

        AreaDisc(ConfigurationSection grenade) {
            ConfigurationSection visual = Configs.requireSection(grenade, "visuals.area_visual");
            this.particle = ParticleBuilder.of(Particle.valueOf(Configs.requireString(visual, "particle").toUpperCase()));
            this.ringSpacing = Configs.requireDouble(grenade, "area_ring_spacing");
            this.pointsPerBlock = Configs.requireInt(grenade, "area_points_per_block");
            this.jitter = Configs.requireDouble(grenade, "area_jitter");
        }

        void play(Location center, double radius) {
            particle.disc(center, radius, ringSpacing, pointsPerBlock, jitter);
        }
    }

    private class GrenadeProjectile extends Projectile {
        private final boolean isMolotov;

        GrenadeProjectile(ConfigurationSection config, HeroSkin skin, boolean isMolotov) {
            super(config, skin);
            this.isMolotov = isMolotov;
        }

        @Override
        public void onHitWall(Hero hero, Location location) {
            super.onHitWall(hero, location);
            onGrenadeLand(hero, location, isMolotov);
        }

        @Override
        public void onExpire(Hero hero, Location location) {
            super.onExpire(hero, location);
            onGrenadeLand(hero, location, isMolotov);
        }

        @Override
        public void onHit(Hero attacker, LivingEntity victim, Location hitLoc) {
            hitSound.play(victim.getEyeLocation(), attacker.getController());
            attacker.triggerAbilityHit(this, victim);
            detonateAoe(attacker, hitLoc, null);
            onGrenadeLand(attacker, victim.getLocation(), isMolotov);
        }
    }

    private class DotZone {
        private final Hero owner;
        private final Location center;
        private final Hitbox hitbox;
        private int ticksLeft;

        DotZone(Hero owner, Location center, int durationTicks) {
            this.owner = owner;
            this.center = center;
            this.ticksLeft = durationTicks;
            LivingEntity self = owner.getController().getEntity();
            this.hitbox = Hitbox.sphere(center, dotRadius)
                    .exclude(self)
                    .excludeTeammates(self)
                    .trackHits(dotHitIntervalTicks * 50L)
                    .onHit(victim -> {
                        dotHitSound.play(victim.getEyeLocation(), owner.getController());
                        owner.triggerAbilityHit(PunishgersSecondary.this, victim);
                        Interactions.on(self, victim).abilityName(PunishgersSecondary.this.getName()).damage(dotDamage).execute();
                    });
        }

        boolean tick() {
            if (--ticksLeft <= 0) return true;
            LivingEntity self = owner.getController() != null ? owner.getController().getEntity() : null;
            if (self == null) return true;

            if (ticksLeft % 5 == 0) molotovArea.play(center, dotRadius);
            hitbox.check();
            return false;
        }
    }
}
