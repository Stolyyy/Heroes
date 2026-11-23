package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.vfx.effect.BlockDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import me.stolyy.heroes.physics.Hitbox;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;


public abstract class Shockwave extends BaseAbility {
    protected double height;
    protected boolean piercesWalls;
    protected boolean showBlockWave;
    
    protected SoundEffect activationSound, hitSound;
    protected VisualEffect waveVisual, hitVisual;

    public Shockwave(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.height = this.config.getDouble("height", 2.0);
        this.piercesWalls = this.config.getBoolean("pierces_walls", false);
        this.showBlockWave = this.config.getBoolean("show_block_wave", true);

        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.hitSound = new SoundEffect(this.config.getConfigurationSection("visuals.hit_sound"));
        
        this.waveVisual = new VisualEffect(this.config.getConfigurationSection("visuals.wave_visual"));
        this.hitVisual = new VisualEffect(this.config.getConfigurationSection("visuals.hit_visual"));
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        Location center = hero.getController().getEntity().getLocation();
        new ShockwaveTask(hero, center).runTaskTimer(Heroes.getInstance(), 0, 1);
    }

    private class ShockwaveTask extends BukkitRunnable {
        private final Hero hero;
        private final Location center;
        private double currentRadius = 0;
        private final double expansionPerTick = radius / 5.0;
        private final Hitbox hitbox;

        public ShockwaveTask(Hero hero, Location center) {
            this.hero = hero;
            this.center = center;
            this.hitbox = Hitbox.cylinder(center, new Vector(0, 1, 0), radius, height)
                    .exclude(hero.getController().getEntity())
                    .excludeTeammates(hero.getController().getEntity())
                    .checkWalls(!piercesWalls)
                    .trackHits()
                    .onHit((victim) -> onHit(hero, victim, center));
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
                Block b = loc.getBlock();
                if (b.getType().isAir()) b = loc.clone().add(0, -1, 0).getBlock();
                if (!b.getType().isAir() && b.getType().isSolid()) {
                    Location spawnLoc = b.getLocation().add(0.5, 0, 0.5);
                    BlockDisplayBuilder.create(spawnLoc)
                            .block(b.getType())
                            .duration(5)
                            .translation(0, 1.0f, 0)
                            .brightness(15, 15)
                            .spawn();
                }
            }
        }
    }

    protected double calcDamage(Hero hero, org.bukkit.entity.LivingEntity victim) { return damage; }
    protected double calcKnockback(Hero hero, org.bukkit.entity.LivingEntity victim) { return knockback; }

    protected void applyInteraction(Hero hero, org.bukkit.entity.LivingEntity victim, Location center) {
        double dmg = calcDamage(hero, victim);
        double kb = calcKnockback(hero, victim);
        Interactions.on(hero.getController().getEntity(), victim).abilityName(getName()).damage(dmg).knockback(kb).execute();
    }

    protected void onHit(Hero hero, org.bukkit.entity.LivingEntity victim, Location center) {
        hitSound.play(victim.getEyeLocation(), hero.getController());
        hitVisual.play(victim.getEyeLocation());
        applyInteraction(hero, victim, center);
    }

    @Override public void onHit(Hero attacker, org.bukkit.entity.LivingEntity victim) { }
}