package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.hero.HeroType;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.PlayerController;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;

public class Attributes {
    private HeroType type;
    private double weight;
    private double maxHealth;
    private double baseMaxHealth;
    private double movementSpeed;
    private double baseMovementSpeed;
    private double jumpHeight;
    private double baseJumpHeight;
    private double gravity;
    private double baseGravity;
    private double stepHeight;
    private double baseStepHeight;
    private double scale;
    private double baseScale;
    private double damageMultiplier = 1.0;
    private double damageDealtMultiplier = 1.0;
    private double knockbackMultiplier = 1.0;
    // Attacker-side counterpart to knockbackMultiplier (kb taken): scales all knockback this
    // hero DEALS. Applied in Interactions.applyKnockback.
    private double knockbackDealtMultiplier = 1.0;
    // Layered on top of movementSpeed in setMovementSpeed (effective = speed * multiplier) —
    // an easier tuning lever for buffs/slows than juggling the raw attribute values.
    private double speedMultiplier = 1.0;

    public Attributes(HeroStats.AttributeStats stats) {
        apply(stats);
    }

    public void apply(HeroStats.AttributeStats stats) {
        this.type = stats.type();
        this.weight = stats.weight();
        this.maxHealth = stats.maxHealth();
        this.baseMaxHealth = stats.maxHealth();
        this.movementSpeed = stats.movementSpeed();
        this.baseMovementSpeed = stats.movementSpeed();
        this.jumpHeight = stats.jumpHeight();
        this.baseJumpHeight = stats.jumpHeight();
        this.gravity = stats.gravity();
        this.baseGravity = stats.gravity();
        this.stepHeight = stats.stepHeight();
        this.baseStepHeight = stats.stepHeight();
        this.scale = stats.scale();
        this.baseScale = stats.scale();
    }

    /**
     * Applies all current attribute values to the controller's entity.
     */
    public void applyAttributes(Controller controller) {
        setMaxHealth(controller, maxHealth);
        setMovementSpeed(controller, movementSpeed);
        setJumpHeight(controller, jumpHeight);
        setGravity(controller, gravity);
        setStepHeight(controller, stepHeight);
        setScale(controller, scale);
    }

    public HeroType getType() { return type; }
    public void setType(HeroType type) { this.type = type; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }

    public double getMaxHealth() { return maxHealth; }
    public void setMaxHealth(Controller controller, double maxHealth) { 
        this.maxHealth = maxHealth;
        AttributeInstance healthAttr = controller.getEntity().getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) healthAttr.setBaseValue(maxHealth);
        
        if (controller.isPlayer() && controller instanceof PlayerController pc) {
            pc.getPlayer().setHealthScale(20.0);
            pc.getPlayer().setHealthScaled(true);
        }
    }

    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(Controller controller, double movementSpeed) {
        this.movementSpeed = movementSpeed;

        AttributeInstance speedAttr = controller.getEntity().getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(movementSpeed * speedMultiplier);
    }

    /** Sets the speed multiplier and immediately re-applies the effective speed to the entity. */
    public void setSpeedMultiplier(Controller controller, double multiplier) {
        this.speedMultiplier = multiplier;
        setMovementSpeed(controller, movementSpeed);
    }
    public double getSpeedMultiplier() { return speedMultiplier; }

    public double getJumpHeight() { return jumpHeight; }
    public void setJumpHeight(Controller controller, double jumpHeight) { 
        this.jumpHeight = jumpHeight;
        AttributeInstance jumpAttr = controller.getEntity().getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) jumpAttr.setBaseValue(jumpHeight);
    }

    public double getGravity() { return gravity; }
    public void setGravity(Controller controller, double gravity) { 
        this.gravity = gravity;
        AttributeInstance gravityAttr = controller.getEntity().getAttribute(Attribute.GRAVITY);
        if (gravityAttr != null) gravityAttr.setBaseValue(gravity);
    }

    public double getStepHeight() { return stepHeight; }
    public void setStepHeight(Controller controller, double stepHeight) {
        this.stepHeight = stepHeight;
        AttributeInstance stepAttr = controller.getEntity().getAttribute(Attribute.STEP_HEIGHT);
        if (stepAttr != null) stepAttr.setBaseValue(stepHeight);
    }

    public double getScale() { return scale; }
    public void setScale(Controller controller, double scale) {
        this.scale = scale;
        AttributeInstance scaleAttr = controller.getEntity().getAttribute(Attribute.SCALE);
        if (scaleAttr != null) scaleAttr.setBaseValue(scale);
    }

    public void resetMaxHealth(Controller controller) { setMaxHealth(controller, baseMaxHealth); }
    public void resetMovementSpeed(Controller controller) { setMovementSpeed(controller, baseMovementSpeed); }
    public void resetJumpHeight(Controller controller) { setJumpHeight(controller, baseJumpHeight); }
    public void resetGravity(Controller controller) { setGravity(controller, baseGravity); }
    public void resetStepHeight(Controller controller) { setStepHeight(controller, baseStepHeight); }
    public void resetScale(Controller controller) { setScale(controller, baseScale); }
    public void resetDamageMultiplier() { this.damageMultiplier = 1.0; }
    public void resetDamageDealtMultiplier() { this.damageDealtMultiplier = 1.0; }
    public void resetKnockbackMultiplier() { this.knockbackMultiplier = 1.0; }
    public void resetKnockbackDealtMultiplier() { this.knockbackDealtMultiplier = 1.0; }

    /**
     * Resets every combat/speed multiplier to 1.0. Called from Hero.resetStats (game start)
     * because Attributes.apply() only restores stat-backed values — without this, buff
     * multipliers would drift across games. Buffs (charms, ults) activate after resetStats.
     */
    public void resetMultipliers() {
        this.damageMultiplier = 1.0;
        this.damageDealtMultiplier = 1.0;
        this.knockbackMultiplier = 1.0;
        this.knockbackDealtMultiplier = 1.0;
        this.speedMultiplier = 1.0;
    }

    public double getBaseMaxHealth() { return baseMaxHealth; }
    public double getBaseMovementSpeed() { return baseMovementSpeed; }
    public double getBaseJumpHeight() { return baseJumpHeight; }
    public double getBaseGravity() { return baseGravity; }
    public double getBaseStepHeight() { return baseStepHeight; }
    public double getBaseScale() { return baseScale; }

    public double getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(double multiplier) { this.damageMultiplier = multiplier; }

    public double getDamageDealtMultiplier() { return damageDealtMultiplier; }

    public void setDamageDealtMultiplier(double multiplier) { this.damageDealtMultiplier = multiplier; }

    public double getKnockbackMultiplier() { return knockbackMultiplier; }


    public void setKnockbackMultiplier(double multiplier) { this.knockbackMultiplier = multiplier; }

    public double getKnockbackDealtMultiplier() { return knockbackDealtMultiplier; }
    public void setKnockbackDealtMultiplier(double multiplier) { this.knockbackDealtMultiplier = multiplier; }

}

    