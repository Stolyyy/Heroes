package me.stolyy.heroes.ability.kit.botmon;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.util.Vector;

public class Botglide extends DoubleJump {
    private final int energyThreshold;

    private final double glideStrength;
    private final int maxGlideTicks;

    private final SoundEffect glideStartSound;
    private final SoundEffect glideLoopSound;
    private final int loopSoundInterval;
    private final VisualEffect glideVisual;

    private boolean isGliding = false;
    private int glideTicks = 0;
    private int ticksSinceLoopSound = 0;

    private Vector glideDirection;

    public Botglide(HeroStats.JumpStats stats, ConfigurationSection config) {
        super(stats);

        this.energyThreshold = Configs.requireInt(config, "energy_threshold");
        this.glideStrength = Configs.requireDouble(config, "glide_strength");
        this.maxGlideTicks = Configs.requireInt(config, "max_glide_ticks");
        this.loopSoundInterval = Configs.requireInt(config, "loop_sound_interval");

        this.glideStartSound = new SoundEffect(Configs.requireSection(config, "visuals.start_sound"));
        this.glideLoopSound = SoundEffect.optional(config, "visuals.loop_sound");
        this.glideVisual = new VisualEffect(Configs.requireSection(config, "visuals.glide_visual"));
    }

    @Override
    public boolean canJump(Hero hero) {
        if (jumpsUsed < maxJumps) return true;
        return hero.getEnergy().getCurrentEnergy() >= energyThreshold;
    }

    private boolean shouldGlide(Hero hero) {
        return hero.getEnergy().getCurrentEnergy() >= energyThreshold;
    }

    @Override
    public boolean performJump(Hero hero) {
        if (isRooted()) return false;
        if (hero.isRespawning()) return false;
        if (!canJump(hero)) return false;

        if (shouldGlide(hero)) {
            hero.getEnergy().tryConsume(hero.getController(), energyThreshold);
            startGlide(hero);
        } else {
            applyVelocity(hero);
            playEffects(hero);
            payCost(hero);
        }

        return true;
    }

    private void startGlide(Hero hero) {
        isGliding = true;
        glideTicks = 0;
        ticksSinceLoopSound = 0;

        Controller controller = hero.getController();
        glideDirection = controller.getLocation().getDirection().normalize();
        applyGlideVelocity(controller);

        glideStartSound.play(controller.getLocation(), controller);
    }

    private void applyGlideVelocity(Controller controller) {
        Vector velocity = glideDirection.clone().multiply(glideStrength);
        controller.setVelocity(velocity);
    }

    @Override
    public void tick(Hero hero) {
        Controller controller = hero.getController();
        if (controller == null) return;

        if (isGliding) {
            tickGlide(controller);
        }

        if (canJump(hero)) {
            controller.setAllowFlight(true);
        }
    }

    private void tickGlide(Controller controller) {
        glideTicks++;

        if (glideTicks >= maxGlideTicks) {
            endGlide();
            return;
        }

        applyGlideVelocity(controller);
        glideVisual.play(controller.getLocation(), glideDirection);

        ticksSinceLoopSound++;
        if (ticksSinceLoopSound >= loopSoundInterval) {
            ticksSinceLoopSound = 0;
            glideLoopSound.play(controller.getLocation(), controller);
        }
    }

    private void endGlide() {
        isGliding = false;
        glideTicks = 0;
        ticksSinceLoopSound = 0;
    }

    @Override
    public void onLand(Hero hero) {
        super.onLand(hero);
        endGlide();
    }
}
