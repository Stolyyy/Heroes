package me.stolyy.heroes.ability.kit.botmon;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.CompositeAbility;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.physics.Pull;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class Botclaw extends CompositeAbility {
    private final double grappleRange;
    private final double grappleSpeed;
    private final double arrivalThreshold;
    private final double cancelYBoost;
    private final double maxGrappleRange;

    private final double ropeModelLength;
    private final boolean ropeCenterOrigin;
    private final double ropeAnchorY;

    private final int bombCount;
    private final int bombStartDelayTicks;
    private final int bombIntervalTicks;
    private final GrappleBomb bombTemplate;

    private final SoundEffect grappleStartSound;
    private final SoundEffect grappleLoopSound;
    private final int loopSoundInterval;
    private final VisualEffect grappleLineVisual;

    private boolean isGrappling = false;
    private Location targetLocation = null;
    private Location grappleStartLocation = null;
    private int grappleTicks = 0;
    private int ticksSinceLoopSound = 0;
    private int bombsFired = 0;
    private int bombTickCounter = 0;
    private ItemDisplay ropeDisplay = null;
    private float ropeThickness = 1.0f;

    public Botclaw(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.grappleRange = Configs.requireDouble(this.config, "grapple_range");
        this.grappleSpeed = Configs.requireDouble(this.config, "grapple_speed");
        this.arrivalThreshold = Configs.requireDouble(this.config, "arrival_threshold");
        this.cancelYBoost = Configs.requireDouble(this.config, "y_boost_on_end");
        this.maxGrappleRange = Configs.requireDouble(this.config, "max_grapple_range");

        this.bombCount = Configs.requireInt(this.config, "bomb_count");
        this.bombStartDelayTicks = Configs.requireInt(this.config, "bomb_start_delay_ticks");
        this.bombIntervalTicks = Configs.requireInt(this.config, "bomb_interval_ticks");
        this.bombTemplate = addChild(new GrappleBomb(Configs.requireSection(this.config, "bomb"), skin));

        this.loopSoundInterval = Configs.requireInt(this.config, "loop_sound_interval");
        this.grappleStartSound = SoundEffect.optional(this.config, "visuals.start_sound");
        this.grappleLoopSound = SoundEffect.optional(this.config, "visuals.loop_sound");

        ConfigurationSection lineConfig = Configs.requireSection(this.config, "visuals.line_visual");
        this.grappleLineVisual = new VisualEffect(lineConfig);

        this.ropeModelLength = Configs.requireDouble(lineConfig, "model_length");
        this.ropeCenterOrigin = "CENTER".equalsIgnoreCase(Configs.requireString(lineConfig, "model_origin"));
        this.ropeAnchorY = Configs.requireDouble(lineConfig, "anchor_y");
    }

    @Override
    protected boolean canUse(Hero hero) {
        Ability ult = hero.getSmashAbility();
        if (ult != null && (ult.getCooldownObject().isInUse()
                || (ult instanceof BaseAbility base && base.isWindingUp()))) {
            ult.getCooldownObject().sendNotReadyVisuals(hero, ult.getName());
            return false;
        }
        Location eye = hero.getController().getEyeLocation();
        RayTraceResult result = WallDetection.rayTraceBlock(eye, eye.getDirection(), grappleRange);
        return result != null && result.getHitBlock() != null;
    }

    @Override
    public void perform(Hero hero) {
        Location eye = hero.getController().getEyeLocation();
        RayTraceResult result = WallDetection.rayTraceBlock(eye, eye.getDirection(), grappleRange);
        if (result == null || result.getHitBlock() == null) return;

        targetLocation = result.getHitPosition().toLocation(eye.getWorld());
        isGrappling = true;
        grappleTicks = 0;
        ticksSinceLoopSound = 0;
        bombsFired = 0;
        bombTickCounter = 0;
        grappleStartLocation = hero.getController().getLocation().clone();

        grappleStartSound.play(hero.getController().getLocation(), hero.getController());

        ropeDisplay = grappleLineVisual.spawnDisplay(
                ropeAnchor(hero.getController().getLocation()), hero.getSkin().getId());
        ropeDisplay.setInterpolationDuration(1);
        ropeThickness = ropeDisplay.getTransformation().getScale().y();
    }

    @Override
    protected void onCompositeDeath(Hero hero) {
        resetGrappleState();
    }

    @Override
    protected void onCompositeOwnerLost(Hero hero) {
        resetGrappleState();
    }

    @Override
    protected void onCompositeTick(Hero hero) {
        if (!isGrappling) return;

        bombTickCounter++;
        while (bombsFired < bombCount) {
            int nextBombTick = bombStartDelayTicks + (bombsFired * bombIntervalTicks);
            if (bombTickCounter >= nextBombTick) {
                bombsFired++;
                bombTemplate.execute(hero);
            } else {
                break;
            }
        }

        tickGrapple(hero);
    }

    private void tickGrapple(Hero hero) {
        if (hero.getController().isSneaking()) {
            endGrapple(hero);
            return;
        }

        grappleTicks++;
        if (grappleTicks * 50L >= cooldown.getCooldownDuration() - 1000) {
            endGrapple(hero);
            return;
        }

        Location currentLocation = hero.getController().getLocation();
        if (grappleStartLocation != null && currentLocation.distance(grappleStartLocation) > maxGrappleRange) {
            endGrapple(hero);
            return;
        }

        if (Pull.toward(hero.getController(), targetLocation, grappleSpeed, arrivalThreshold)) {
            endGrapple(hero);
            return;
        }

        stretchRope(currentLocation);

        ticksSinceLoopSound++;
        if (ticksSinceLoopSound >= loopSoundInterval) {
            ticksSinceLoopSound = 0;
            grappleLoopSound.play(currentLocation, hero.getController());
        }
    }

    private Location ropeAnchor(Location entityLocation) {
        return entityLocation.clone().add(0, ropeAnchorY, 0);
    }

    private void stretchRope(Location entityLocation) {
        AbstractDisplayBuilder.span(ropeDisplay, ropeAnchor(entityLocation), targetLocation,
                ropeModelLength, ropeThickness, ropeCenterOrigin);
    }

    private void removeRope() {
        if (ropeDisplay != null) {
            ropeDisplay.remove();
            ropeDisplay = null;
        }
    }

    private void endGrapple(Hero hero) {
        if (!isGrappling) return;

        resetGrappleState();

        Vector currentVelocity = hero.getController().getVelocity();
        currentVelocity.setY(cancelYBoost);
        hero.getController().setVelocity(currentVelocity);
    }

    private void resetGrappleState() {
        isGrappling = false;
        removeRope();
        targetLocation = null;
        grappleStartLocation = null;
        grappleTicks = 0;
        ticksSinceLoopSound = 0;
        bombsFired = 0;
        bombTickCounter = 0;
    }

    @Override
    public void onAbilityUse(Hero hero, Ability ability) {
        if (isGrappling && ability.getType() == AbilityType.SMASH) {
            endGrapple(hero);
        }
    }

    private static class GrappleBomb extends Projectile {
        public GrappleBomb(ConfigurationSection config, HeroSkin skin) {
            super(config, skin, AbilityType.SECONDARY);
        }
    }
}
