package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.game.PracticeArena;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.CompositeAbility;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.mechanics.StatusEffects;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.ParticleBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PunishgersSecondary extends CompositeAbility {
    @FunctionalInterface
    private interface GrenadeLanding {
        void onLand(Hero hero, Location loc, @Nullable LivingEntity directVictim);
    }

    private final GrenadeProjectile molotov;
    private final GrenadeProjectile stunGrenade;

    private final double dotDamage;
    private final double dotRadius;
    private final int dotDurationTicks;
    private final int dotHitIntervalTicks;
    private final AreaDisc molotovArea;
    private final SoundEffect dotHitSound;

    private final double stunRadius;
    private final double stunLaunchKnockback;
    private final StatusEffects.Spec stunStatus;
    private final long disorientMs;
    private final PotionEffectType disorientEffect;
    private final AreaDisc stunArea;
    private final SoundEffect stunHitSound;

    private final List<DotZone> dotZones = new ArrayList<>();

    private PunishgersWeapon weapon;

    public PunishgersSecondary(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        ConfigurationSection molotovConfig = Configs.requireSection(this.config, "molotov");
        ConfigurationSection stunConfig = Configs.requireSection(this.config, "stun");

        this.dotDamage = Configs.requireDouble(molotovConfig, "dot_damage");
        this.dotRadius = Configs.requireDouble(molotovConfig, "dot_radius");
        this.dotDurationTicks = (int) (Configs.requireDouble(molotovConfig, "dot_duration") * 20);
        this.dotHitIntervalTicks = Configs.requireInt(molotovConfig, "dot_hit_interval");
        this.molotovArea = new AreaDisc(molotovConfig);
        this.dotHitSound = new SoundEffect(Configs.requireSection(molotovConfig, "visuals.dot_hit_sound"));

        this.stunRadius = Configs.requireDouble(stunConfig, "effect_radius");
        this.stunLaunchKnockback = Configs.requireDouble(stunConfig, "launch_knockback");
        this.stunStatus = StatusEffects.Spec.fromConfig(stunConfig);
        this.disorientMs = Configs.requireLong(stunConfig, "disorient_ms");
        this.disorientEffect = switch (Configs.requireString(stunConfig, "disorient_effect").toUpperCase()) {
            case "NAUSEA" -> PotionEffectType.NAUSEA;
            case "BLINDNESS" -> PotionEffectType.BLINDNESS;
            default -> throw new IllegalStateException("disorient_effect must be NAUSEA or BLINDNESS");
        };
        this.stunArea = new AreaDisc(stunConfig);
        this.stunHitSound = new SoundEffect(Configs.requireSection(stunConfig, "visuals.effect_hit_sound"));

        this.molotov = addChild(new GrenadeProjectile(molotovConfig, skin,
                (h, loc, victim) -> {
                    if (PracticeManager.isWithinPracticeBounds(h, loc)) {
                        dotZones.add(new DotZone(h, loc.clone(), dotDurationTicks));
                    }
                }));
        this.stunGrenade = addChild(new GrenadeProjectile(stunConfig, skin, this::detonateStun));
    }

    @Override
    public void initialize(Hero hero) {
        this.weapon = hero.findAbility(PunishgersWeapon.class).orElse(null);
    }

    @Override
    protected boolean canUse(Hero hero) {
        return weapon == null || !weapon.isSwapping();
    }

    @Override
    public void perform(Hero hero) {
        boolean shotgunMode = weapon != null && weapon.isShotgun();
        (shotgunMode ? stunGrenade : molotov).perform(hero);
    }

    @Override
    protected void onCompositeTick(Hero hero) {
        for (DotZone zone : new ArrayList<>(dotZones)) {
            if (zone.tick()) dotZones.remove(zone);
        }
    }

    @Override
    protected void onCompositeOwnerLost(Hero hero) {
        dotZones.clear();
        StatusEffects.removeAllFrom(this);
    }

    private void detonateStun(Hero owner, Location center, @Nullable LivingEntity directHitVictim) {
        PracticeArena arena = PracticeManager.practiceArenaOf(owner);
        if (arena != null && !arena.contains(center)) return;

        double visualRadius = arena != null
                ? Math.min(stunRadius, arena.maxRadiusInsideXZ(center))
                : stunRadius;
        if (visualRadius > 0) stunArea.play(center, visualRadius);

        LivingEntity self = owner.getController().getEntity();
        Hitbox.sphere(center, stunRadius)
                .exclude(self)
                .excludeTeammates(self)
                .filter(victim -> arena == null || arena.contains(victim.getLocation()))
                .onHit(victim -> {
                    stunHitSound.play(victim.getEyeLocation(), owner.getController());
                    if (!victim.equals(directHitVictim)) owner.triggerAbilityHit(this, victim);
                    Interactions.on(self, victim)
                            .verticalKnockback(stunLaunchKnockback)
                            .staticKnockback()
                            .execute();
                    victim.addPotionEffect(new PotionEffect(disorientEffect,
                            (int) (disorientMs / 50), 0, false, false, false));
                    StatusEffects.applyTo(victim, stunStatus, this, owner, null);
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
        private final GrenadeLanding landing;

        GrenadeProjectile(ConfigurationSection config, HeroSkin skin, GrenadeLanding landing) {
            super(config, skin, AbilityType.SECONDARY);
            this.landing = landing;
        }

        @Override
        protected void applyInteraction(Hero attacker, LivingEntity victim, Location hitLoc) {
            attacker.triggerAbilityHit(this, victim);
        }

        @Override
        protected void onImpact(Hero hero, Location location, @Nullable LivingEntity victim, ImpactCause cause) {
            landing.onLand(hero, victim != null ? victim.getLocation() : location, victim);
        }
    }

    private class DotZone {
        private final Hero owner;
        private final Location center;
        private final @Nullable PracticeArena arena;
        private final Hitbox hitbox;
        private int ticksLeft;

        DotZone(Hero owner, Location center, int durationTicks) {
            this.owner = owner;
            this.center = center;
            this.arena = PracticeManager.practiceArenaOf(owner);
            this.ticksLeft = durationTicks;
            LivingEntity self = owner.getController().getEntity();
            this.hitbox = Hitbox.sphere(center, dotRadius)
                    .exclude(self)
                    .excludeTeammates(self)
                    .filter(victim -> arena == null || arena.contains(victim.getLocation()))
                    .trackHits(dotHitIntervalTicks * 50L)
                    .onHit(victim -> {
                        dotHitSound.play(victim.getEyeLocation(), owner.getController());
                        PunishgersSecondary.this.hit(owner, victim).damage(dotDamage).execute();
                    });
        }

        boolean tick() {
            if (--ticksLeft <= 0) return true;
            LivingEntity self = owner.getController() != null ? owner.getController().getEntity() : null;
            if (self == null) return true;

            if (ticksLeft % 5 == 0) {
                double visualRadius = arena != null
                        ? Math.min(dotRadius, arena.maxRadiusInsideXZ(center))
                        : dotRadius;
                if (visualRadius > 0) molotovArea.play(center, visualRadius);
            }
            hitbox.check();
            return false;
        }
    }
}
