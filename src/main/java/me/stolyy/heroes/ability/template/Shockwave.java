package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.BlockDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Directional ground shockwave template sweeping consecutive column checks and optional rising
 * floor block displays.
 */
public class Shockwave extends BaseAbility {
    protected double height;
    protected boolean piercesWalls;
    protected boolean showBlockWave;
    
    protected SoundEffect activationSound, hitSound;
    protected VisualEffect waveVisual, hitVisual;

    public Shockwave(ConfigurationSection config, HeroSkin skin) {
        this(config, skin, null);
    }

    public Shockwave(ConfigurationSection config, HeroSkin skin, AbilityType explicitType) {
        super(config, skin, explicitType);
        this.height = Configs.orTemplate(this.config, "height", "shockwave.height");
        this.piercesWalls = Configs.orTemplateBoolean(this.config, "pierces_walls", "shockwave.pierces_walls");
        this.showBlockWave = Configs.orTemplateBoolean(this.config, "show_block_wave", "shockwave.show_block_wave");

        this.activationSound = SoundEffect.optional(this.config, "visuals.activation_sound");
        this.hitSound = SoundEffect.optional(this.config, "visuals.hit_sound");

        this.waveVisual = VisualEffect.optional(this.config, "visuals.wave_visual");
        this.hitVisual = VisualEffect.optional(this.config, "visuals.hit_visual");
    }

    @Override
    public void perform(Hero hero) {
        perform(hero, null);
    }

    public void perform(Hero hero, @Nullable Map<UUID, Long> sharedHitHistory) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        Location center = hero.getController().getLocation();
        new ShockwaveTask(hero, center, sharedHitHistory).runTaskTimer(Heroes.getInstance(), 0, 1);
    }

    private class ShockwaveTask extends BukkitRunnable {
        private final Hero hero;
        private final Location center;
        private double currentRadius = 0;
        private final double expansionPerTick = radius / 5.0;
        private final Hitbox hitbox;

        public ShockwaveTask(Hero hero, Location center, @Nullable Map<UUID, Long> sharedHitHistory) {
            this.hero = hero;
            this.center = center;
            this.hitbox = Hitbox.cylinder(center, new Vector(0, 1, 0), radius, height)
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .checkWalls(!piercesWalls)
                    .onHit((victim) -> onHit(hero, victim, center));
            if (sharedHitHistory != null) {
                this.hitbox.shareHitHistory(sharedHitHistory);
            } else {
                this.hitbox.trackHits();
            }
        }

        @Override
        public void run() {
            if (currentRadius >= radius) {
                cancel();
                return;
            }
            currentRadius += expansionPerTick;
            hitbox.check();
            
            // Wave Visual
            waveVisual.play(center, new Vector(0, 1, 0), currentRadius, hero.getSkin().getId());

            if (showBlockWave) {
                spawnBlockWave(currentRadius);
            }
        }

        private void spawnBlockWave(double r) {
            int points = (int) (r * 8);
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI / points) * i;
                double x = center.getX() + Math.cos(angle) * r;
                double z = center.getZ() + Math.sin(angle) * r;
                Location loc = new Location(center.getWorld(), x, center.getY() - 1, z);
                BlockDisplayBuilder.riseAndFall(loc, 1.0f, 5);
            }
        }
    }

    protected void applyInteraction(Hero hero, LivingEntity victim, Location center) {
        Interactions.InteractionBuilder interaction = hit(hero, victim);
        double dmg = calcDamage(hero, victim);
        double kb = calcKnockback(hero, victim);
        interaction.damage(dmg).knockback(kb).execute();
    }

    protected void onHit(Hero hero, LivingEntity victim, Location center) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());
        applyInteraction(hero, victim, center);
    }
}