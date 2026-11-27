package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.hero.HeroType;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.util.ModifierStack;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;

public class Attributes {
    private HeroType type;
    private double baseWeight;
    private double baseMaxHealth;
    private double baseMovementSpeed;
    private double baseJumpHeight;
    private double baseGravity;
    private double baseStepHeight;
    private double baseScale;

    private final ModifierStack weightMods = new ModifierStack();
    private final ModifierStack movementSpeedMods = new ModifierStack();
    private final ModifierStack jumpStrengthMods = new ModifierStack();
    private final ModifierStack gravityMods = new ModifierStack();
    private final ModifierStack stepHeightMods = new ModifierStack();
    private final ModifierStack damageTakenMods = new ModifierStack();
    private final ModifierStack damageDealtMods = new ModifierStack();
    private final ModifierStack knockbackTakenMods = new ModifierStack();
    // Attacker-side counterpart to knockbackTakenMods: scales all knockback this hero DEALS.
    private final ModifierStack knockbackDealtMods = new ModifierStack();

    public Attributes(HeroStats.AttributeStats stats) {
        apply(stats);
    }

    /** Updates base attribute values without discarding active keyed modifier stacks. */
    public void apply(HeroStats.AttributeStats stats) {
        this.type = stats.type();
        this.baseWeight = stats.weight();
        this.baseMaxHealth = stats.maxHealth();
        this.baseMovementSpeed = stats.movementSpeed();
        this.baseJumpHeight = stats.jumpHeight();
        this.baseGravity = stats.gravity();
        this.baseStepHeight = stats.stepHeight();
        this.baseScale = stats.scale();
    }

    /**
     * Applies all effective attribute values to the controller's entity.
     */
    public void applyAttributes(Controller controller) {
        writeBase(controller, Attribute.MAX_HEALTH, baseMaxHealth);
        if (controller != null && controller.isPlayer() && controller instanceof PlayerController pc
                && controller.getEntity() != null) {
            pc.getPlayer().setHealthScale(20.0);
            pc.getPlayer().setHealthScaled(true);
        }
        syncSpeed(controller);
        syncJumpStrength(controller);
        syncGravity(controller);
        syncStepHeight(controller);
        writeBase(controller, Attribute.SCALE, baseScale);
    }

    public HeroType getType() { return type; }
    public void setType(HeroType type) { this.type = type; }

    public double getWeight() { return weightMods.evaluate(baseWeight, 0.0); }
    public double getBaseWeight() { return baseWeight; }
    public void addWeightMultiplier(Object key, double mult) { weightMods.putMult(key, mult); }
    public void addWeightFlat(Object key, double delta) { weightMods.putFlat(key, delta); }
    public void removeWeightModifier(Object key) { weightMods.remove(key); }

    public double getMaxHealth() { return baseMaxHealth; }
    public double getBaseMaxHealth() { return baseMaxHealth; }

    public double getMovementSpeed() { return movementSpeedMods.evaluate(baseMovementSpeed, 0.0); }
    public double getBaseMovementSpeed() { return baseMovementSpeed; }

    public void addSpeedMultiplier(Controller controller, Object key, double mult) {
        movementSpeedMods.putMult(key, mult);
        syncSpeed(controller);
    }

    public void addSpeedFlat(Controller controller, Object key, double delta) {
        movementSpeedMods.putFlat(key, delta);
        syncSpeed(controller);
    }

    public void removeSpeedModifier(Controller controller, Object key) {
        movementSpeedMods.remove(key);
        syncSpeed(controller);
    }

    private void syncSpeed(Controller controller) {
        writeBase(controller, Attribute.MOVEMENT_SPEED, getMovementSpeed());
    }

    public double getJumpHeight() { return jumpStrengthMods.evaluate(baseJumpHeight, 0.0); }
    public double getBaseJumpHeight() { return baseJumpHeight; }

    public void addJumpStrengthMultiplier(Controller controller, Object key, double mult) {
        jumpStrengthMods.putMult(key, mult);
        syncJumpStrength(controller);
    }

    public void removeJumpStrengthModifier(Controller controller, Object key) {
        jumpStrengthMods.remove(key);
        syncJumpStrength(controller);
    }

    private void syncJumpStrength(Controller controller) {
        writeBase(controller, Attribute.JUMP_STRENGTH, getJumpHeight());
    }

    public double getGravity() { return gravityMods.evaluate(baseGravity, 0.0); }
    public double getBaseGravity() { return baseGravity; }

    public void addGravityMultiplier(Controller controller, Object key, double mult) {
        gravityMods.putMult(key, mult);
        syncGravity(controller);
    }

    public void addGravityFlat(Controller controller, Object key, double delta) {
        gravityMods.putFlat(key, delta);
        syncGravity(controller);
    }

    public void removeGravityModifier(Controller controller, Object key) {
        gravityMods.remove(key);
        syncGravity(controller);
    }

    private void syncGravity(Controller controller) {
        writeBase(controller, Attribute.GRAVITY, getGravity());
    }

    public double getStepHeight() { return stepHeightMods.evaluate(baseStepHeight, 0.0); }
    public double getBaseStepHeight() { return baseStepHeight; }

    public void addStepHeightMultiplier(Controller controller, Object key, double mult) {
        stepHeightMods.putMult(key, mult);
        syncStepHeight(controller);
    }

    public void addStepHeightFlat(Controller controller, Object key, double delta) {
        stepHeightMods.putFlat(key, delta);
        syncStepHeight(controller);
    }

    public void removeStepHeightModifier(Controller controller, Object key) {
        stepHeightMods.remove(key);
        syncStepHeight(controller);
    }

    private void syncStepHeight(Controller controller) {
        writeBase(controller, Attribute.STEP_HEIGHT, getStepHeight());
    }

    public double getScale() { return baseScale; }
    public double getBaseScale() { return baseScale; }

    private static void writeBase(Controller controller, Attribute attribute, double value) {
        if (controller == null) return;
        LivingEntity entity = controller.getEntity();
        if (entity == null) return;
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public double getDamageMultiplier() { return damageTakenMods.product(); }
    public void addDamageTakenMultiplier(Object key, double mult) { damageTakenMods.putMult(key, mult); }
    public void removeDamageTakenModifier(Object key) { damageTakenMods.remove(key); }

    public double getDamageDealtMultiplier() { return damageDealtMods.product(); }
    public void addDamageDealtMultiplier(Object key, double mult) { damageDealtMods.putMult(key, mult); }
    public void removeDamageDealtModifier(Object key) { damageDealtMods.remove(key); }

    public double getKnockbackMultiplier() { return knockbackTakenMods.product(); }
    public void addKnockbackTakenMultiplier(Object key, double mult) { knockbackTakenMods.putMult(key, mult); }
    public void removeKnockbackTakenModifier(Object key) { knockbackTakenMods.remove(key); }

    public double getKnockbackDealtMultiplier() { return knockbackDealtMods.product(); }
    public void addKnockbackDealtMultiplier(Object key, double mult) { knockbackDealtMods.putMult(key, mult); }
    public void removeKnockbackDealtModifier(Object key) { knockbackDealtMods.remove(key); }

    public void clearModifiers() {
        weightMods.clear();
        movementSpeedMods.clear();
        jumpStrengthMods.clear();
        gravityMods.clear();
        stepHeightMods.clear();
        damageTakenMods.clear();
        damageDealtMods.clear();
        knockbackTakenMods.clear();
        knockbackDealtMods.clear();
    }
}
