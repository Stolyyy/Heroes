package me.stolyy.heroes.ability.kit.marauder;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class MarauderDarkJump extends DoubleJump {
    private final int energyCost;
    private final int maxDarkJumps;
    private int darkJumpsUsed = 0;
    private final double darkStrength;
    private final double darkYStrength;
    private final SoundEffect darkSound;
    private final VisualEffect darkVisual;


    public MarauderDarkJump(HeroStats.JumpStats stats, ConfigurationSection config) {
        super(stats);
        this.energyCost = config.getInt("dark_jump_energy_cost");
        this.maxDarkJumps = config.getInt("max_dark_jumps", 1);
        this.darkStrength = config.getDouble("dark_jump_strength");
        this.darkYStrength = config.getDouble("dark_jump_y_strength");
        this.darkSound = new SoundEffect(config.getConfigurationSection("dark_jump_sound"));
        this.darkVisual = new VisualEffect(config.getConfigurationSection("dark_jump_visuals"));
    }

    @Override
    public boolean canJump(Hero hero) {
        if (jumpsUsed < maxJumps) return true;
        if (darkJumpsUsed >= maxDarkJumps) return false;
        return hero.getEnergy().getCurrentEnergy() >= energyCost;
    }

    @Override
    protected void payCost(Hero hero) {
        if (jumpsUsed < maxJumps) {
            jumpsUsed++;
        } else {
            darkJumpsUsed++;
            hero.getEnergy().tryConsume(hero.getController(), energyCost);
        }
    }

    @Override
    public void resetJumps() {
        super.resetJumps();
        darkJumpsUsed = 0;
    }

    @Override
    protected void applyVelocity(Hero hero) {
        if (jumpsUsed < maxJumps) {
            super.applyVelocity(hero);
        } else {
            Controller controller = hero.getController();
            Vector direction = controller.getLocation().getDirection().multiply(darkStrength)
                    .add(new Vector(0,1,0).multiply(darkYStrength));
            controller.setVelocity(direction);
        }
    }

    @Override
    protected void playEffects(Hero hero) {
        if (jumpsUsed < maxJumps) {
            super.playEffects(hero);
        } else {
            Controller controller = hero.getController();
            darkSound.play(controller.getLocation());
            darkVisual.play(controller.getLocation());
        }
    }

    @Override
    public void tick(Hero hero) {
        if (jumpsUsed < maxJumps || (darkJumpsUsed < maxDarkJumps && hero.getEnergy().getCurrentEnergy() >= energyCost)) {
            hero.getController().setAllowFlight(true);
        }
    }
}
