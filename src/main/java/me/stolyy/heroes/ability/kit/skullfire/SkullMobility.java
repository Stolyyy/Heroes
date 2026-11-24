package me.stolyy.heroes.ability.kit.skullfire;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class SkullMobility extends DoubleJump {
    protected int jumpCap;
    private int currentCap;
    protected double skullXStrength;
    protected double skullYStrength;

    protected SoundEffect skullSound;
    protected VisualEffect skullVisual;

    public SkullMobility(HeroStats.JumpStats stats, ConfigurationSection config) {
        super(stats);
        this.jumpCap = Configs.requireInt(config, "jump_cap");
        this.currentCap = maxJumps;
        this.skullXStrength = config.contains("x_strength")
                ? Configs.requireDouble(config, "x_strength") : xStrength;
        this.skullYStrength = config.contains("y_strength")
                ? Configs.requireDouble(config, "y_strength") : yStrength;

        this.skullSound = SoundEffect.optional(config, "skull_sound");
        this.skullVisual = new VisualEffect(Configs.requireSection(config, "skull_visual"));
    }

    @Override
    public boolean canJump(Hero hero) {
        return jumpsUsed < currentCap;
    }

    @Override
    public boolean performJump(Hero hero) {
        if (isRooted()) return false;
        if (hero.isRespawning()) return false;
        if (jumpsUsed < maxJumps) return super.performJump(hero);
        else return performSkullJump(hero);
    }

    @Override
    public void resetJumps() {
        jumpsUsed = 0;
        currentCap = maxJumps;
    }

    public boolean performSkullJump(Hero hero) {
        if (!canJump(hero)) return false;

        applySkullVelocity(hero);
        playSkullEffects(hero);
        payCost(hero);

        return true;
    }

    protected void applySkullVelocity(Hero hero) {
        Controller controller = hero.getController();
        Vector direction = controller.getLocation().getDirection().multiply(skullXStrength);
        direction.setY(skullYStrength);
        controller.setVelocity(direction);
    }

    protected void playSkullEffects(Hero hero) {
        Controller controller = hero.getController();
        skullSound.play(controller.getLocation());
        skullVisual.play(controller.getLocation());
    }

    public boolean addJump(Hero hero) {
        if (currentCap < jumpCap) {
            currentCap++;
            hero.getController().setAllowFlight(true);
            return true;
        } else {
            return false;
        }
    }
}
