package me.stolyy.heroes.ability.kit.cakemonster;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class Defecake extends DoubleJump {
    private final Ability cake;
    private final int energyCost;

    private boolean energyLaunch;

    private final double defeXStrength;
    private final double defeYStrength;

    protected SoundEffect defeSound;
    protected VisualEffect defeVisual;

    public Defecake(HeroStats.JumpStats stats, ConfigurationSection config, HeroSkin skin) {
        super(stats);
        cake = new CakePoop(config, skin);
        this.energyCost = Configs.requireInt(config, "energy_cost");

        this.defeXStrength = Configs.requireDouble(config, "defe_x_strength");
        this.defeYStrength = Configs.requireDouble(config, "defe_y_strength");

        this.defeSound = SoundEffect.optional(config, "visuals.defe_sound");
        this.defeVisual = new VisualEffect(Configs.requireSection(config, "visuals.defe_visual"));
    }

    @Override
    public boolean canJump(Hero hero) {
        if (hero.getEnergy().getCurrentEnergy() >= energyCost) return true;
        return jumpsUsed < maxJumps;
    }

    @Override
    protected void applyVelocity(Hero hero) {
        energyLaunch = hero.getEnergy().getCurrentEnergy() >= energyCost;
        if(energyLaunch) {
            Controller controller = hero.getController();
            Vector direction = controller.getLocation().getDirection().multiply(defeXStrength);
            direction.setY(defeYStrength);
            controller.setVelocity(direction);
        } else {
            super.applyVelocity(hero);
        }
    }

    @Override
    protected void payCost(Hero hero) {
        if (energyLaunch) return;
        super.payCost(hero);
    }

    @Override
    protected void playEffects(Hero hero) {
        if(hero.getEnergy().getCurrentEnergy() >= energyCost) {
            cake.execute(hero);

            Controller controller = hero.getController();
            defeSound.play(controller.getLocation());

            defeVisual.play(controller.getLocation());
        }
        else {
            super.playEffects(hero);
        }
    }

    @Override
    public void tick(Hero hero) {
        if (jumpsUsed < maxJumps || hero.getEnergy().getCurrentEnergy() >= energyCost) {
            hero.getController().setAllowFlight(true);
        }
        cake.tick(hero);
    }

    @Override
    public void tickDormant(Hero hero) {
        cake.tick(hero);
    }

    @Override
    public void onDeath(Hero hero) {
        cake.onDeath(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        cake.onOwnerLost(hero);
    }


    private static class CakePoop extends Projectile {
        public CakePoop(ConfigurationSection config, HeroSkin skin) {
            super(config, skin, AbilityType.PASSIVE);
        }

        @Override
        public Location getStartLocation(Hero hero) {
            return hero.getController().getLocation();
        }

        @Override
        public Vector getDirection(Location location) {
            return new Vector(0, -1, 0);
        }
    }
}
