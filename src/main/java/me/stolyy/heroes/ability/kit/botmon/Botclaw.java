package me.stolyy.heroes.ability.kit.botmon;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.AbstractDisplayBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Botclaw extends BaseAbility {
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

    private final GrappleCancel cancelPassive;

    private boolean isGrappling = false;
    private Location targetLocation = null;
    private Location grappleStartLocation = null;
    private int grappleTicks = 0;
    private int ticksSinceLoopSound = 0;
    private int bombsFired = 0;
    private int bombTickCounter = 0;
    private Hero currentHero = null;
    private ItemDisplay ropeDisplay = null;
    private float ropeThickness = 1.0f;

    public Botclaw(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.grappleRange = Configs.requireDouble(config, "grapple_range");
        this.grappleSpeed = Configs.requireDouble(config, "grapple_speed");
        this.arrivalThreshold = Configs.requireDouble(config, "arrival_threshold");
        this.cancelYBoost = Configs.requireDouble(config, "y_boost_on_end");
        this.maxGrappleRange = config.getDouble("max_grapple_range", 50.0);

        this.bombCount = Configs.requireInt(config, "bomb_count");
        this.bombStartDelayTicks = Configs.requireInt(config, "bomb_start_delay_ticks");
        this.bombIntervalTicks = Configs.requireInt(config, "bomb_interval_ticks");
        this.bombTemplate = new GrappleBomb(Configs.requireSection(config, "bomb"), skin);

        this.loopSoundInterval = config.getInt("loop_sound_interval", 1000);
        this.grappleStartSound = new SoundEffect(this.config.getConfigurationSection("visuals.start_sound"));
        this.grappleLoopSound = new SoundEffect(this.config.getConfigurationSection("visuals.loop_sound"));

        ConfigurationSection lineConfig = Configs.requireSection(this.config, "visuals.line_visual");
        this.grappleLineVisual = new VisualEffect(lineConfig);

        this.ropeModelLength = Configs.requireDouble(lineConfig, "model_length");
        this.ropeCenterOrigin = "CENTER".equalsIgnoreCase(Configs.requireString(lineConfig, "model_origin"));
        this.ropeAnchorY = Configs.requireDouble(lineConfig, "anchor_y");

        this.cancelPassive = new GrappleCancel(this);
    }

    @Override
    public boolean canExecute(Hero hero) {
        Ability ult = hero.getSmashAbility();
        if (ult.getCooldownObject().isInUse()) {
            ult.getCooldownObject().sendNotReadyVisuals(hero, ult.getName());
            return false;
        }
        return super.canExecute(hero);
    }

    @Override
    public boolean execute(Hero hero) {
        Location eyeLocation = hero.getController().getEyeLocation();
        Vector direction = eyeLocation.getDirection();

        RayTraceResult result = eyeLocation.getWorld().rayTraceBlocks(
                eyeLocation, direction, grappleRange, FluidCollisionMode.NEVER, true);

        if (result == null || result.getHitBlock() == null) {
            // No block in range
            return false;
        }

        if (!canExecute(hero)) return false;
        startCooldown(hero);

        targetLocation = result.getHitPosition().toLocation(eyeLocation.getWorld());
        perform(hero);

        return true;
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);

        removeRope();
        if (isGrappling) {
            isGrappling = false;
            targetLocation = null;
            grappleStartLocation = null;
            grappleTicks = 0;
            ticksSinceLoopSound = 0;
            bombsFired = 0;
            bombTickCounter = 0;
            currentHero = null;
        }
    }

    public void perform(Hero hero) {
        isGrappling = true;
        grappleTicks = 0;
        ticksSinceLoopSound = 0;
        bombsFired = 0;
        bombTickCounter = 0;
        currentHero = hero;
        grappleStartLocation = hero.getController().getLocation().clone();

        grappleStartSound.play(hero.getController().getLocation(), hero.getController());

        ropeDisplay = grappleLineVisual.spawnDisplay(ropeAnchor(hero.getController().getLocation()), hero.getSkin().getId());

        ropeDisplay.setTeleportDuration(1);
        ropeDisplay.setInterpolationDuration(1);

        ropeThickness = ropeDisplay.getTransformation().getScale().y();
    }

    @Override
    protected void onTick(Hero hero) {
        bombTemplate.tick(hero);

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
            cancelGrapple();
            return;
        }

        grappleTicks++;
        if (grappleTicks * 50L >= cooldown.getCooldownDuration() - 1000) {
            endGrapple(hero);
            return;
        }

        LivingEntity entity = hero.getController().getEntity();
        Location currentLocation = entity.getLocation();

        if (grappleStartLocation != null && currentLocation.distance(grappleStartLocation) > maxGrappleRange) {
            endGrapple(hero);
            return;
        }

        double distance = currentLocation.distance(targetLocation);
        if (distance <= arrivalThreshold) {
            endGrapple(hero);
            return;
        }

        Vector direction = targetLocation.toVector().subtract(currentLocation.toVector()).normalize();
        Vector velocity = direction.multiply(grappleSpeed);
        entity.setVelocity(velocity);

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
        Location anchor = ropeAnchor(entityLocation);
        Vector span = targetLocation.toVector().subtract(anchor.toVector());
        double length = span.length();
        Vector spanDir = span.clone().normalize();

        ropeDisplay.teleport(AbstractDisplayBuilder.canonical(anchor));

        Vector3f translation = ropeCenterOrigin
                ? new Vector3f((float) (spanDir.getX() * length / 2.0),
                               (float) (spanDir.getY() * length / 2.0),
                               (float) (spanDir.getZ() * length / 2.0))
                : new Vector3f();

        ropeDisplay.setInterpolationDelay(0);

        ropeDisplay.setTransformation(new Transformation(
                translation,
                AbstractDisplayBuilder.calculateRotation(spanDir),
                AbstractDisplayBuilder.forwardScale((float) (length / ropeModelLength), ropeThickness),
                new Quaternionf()));
    }

    private void removeRope() {
        if (ropeDisplay != null) {
            ropeDisplay.remove();
            ropeDisplay = null;
        }
    }

    public void cancelGrapple() {
        if (!isGrappling || currentHero == null) return;
        endGrapple(currentHero);
    }

    private void endGrapple(Hero hero) {
        if (!isGrappling) return;

        isGrappling = false;
        removeRope();

        LivingEntity entity = hero.getController().getEntity();
        Vector currentVelocity = entity.getVelocity();
        currentVelocity.setY(cancelYBoost);
        entity.setVelocity(currentVelocity);

        targetLocation = null;
        grappleStartLocation = null;
        grappleTicks = 0;
        ticksSinceLoopSound = 0;
        bombTickCounter = 0;
        currentHero = null;
    }

    public boolean isGrappling() {
        return isGrappling;
    }

    @Override
    public void onAbilityUse(Hero hero, Ability ability) {
        if (isGrappling && ability.getType() == AbilityType.SMASH) {
            cancelGrapple();
        }
    }


    private static class GrappleBomb extends Projectile {
        public GrappleBomb(ConfigurationSection config, HeroSkin skin) {
            super(config, skin);
        }
    }

    private static class GrappleCancel implements Ability {
        private final Botclaw parent;
        private final Cooldown cooldown = new Cooldown(0, 0, 0);

        public GrappleCancel(Botclaw parent) {
            this.parent = parent;
        }

        @Override
        public String getName() { return "Grapple Cancel"; }

        @Override
        public String getDescription() { return "Cancel grapple by sneaking"; }

        @Override
        public AbilityType getType() { return AbilityType.PASSIVE_SNEAK; }

        @Override
        public long getCooldown() { return 0; }

        @Override
        public int getEnergyCost() { return 0; }

        @Override
        public Ammo getAmmo() { return null; }

        @Override
        public Cooldown getCooldownObject() { return cooldown; }

        @Override
        public boolean execute(Hero hero) {
            if (!parent.isGrappling()) return false;
            parent.cancelGrapple();
            return true;
        }

        @Override
        public boolean canExecute(Hero hero) {
            return parent.isGrappling();
        }
    }
}
