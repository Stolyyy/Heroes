package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.physics.Interactions;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Objects;

public class GameEffects {
    private static final NamespacedKey RESTRICT_MOVEMENT_KEY = new NamespacedKey(Heroes.getInstance(), "restrict_movement");
    private static final NamespacedKey RESTRICT_JUMP_KEY = new NamespacedKey(Heroes.getInstance(), "restrict_jump");
    private static final NamespacedKey RESTRICT_GRAVITY_KEY = new NamespacedKey(Heroes.getInstance(), "restrict_gravity");

    public static void applyEffects(Player player, PlayerSettings playerSettings){
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        // The health setting is ABSOLUTE starting/max HP (default 100), not a bonus on
        // the hero base — no per-hero derivation, no compounding across respawns.
        AttributeInstance healthAttr = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH));
        double totalHealth = playerSettings.health();
        healthAttr.setBaseValue(totalHealth);
        player.setHealth(Math.max(Interactions.ZERO_HP_BUFFER, totalHealth));
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
        player.setMaximumNoDamageTicks(1);
    }

    public static void removeEffects(Player player){
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setHealthScaled(false);
        player.setMaximumNoDamageTicks(10);
        resetToDefault(player, Attribute.MOVEMENT_SPEED);
        resetToDefault(player, Attribute.JUMP_STRENGTH);
        resetToDefault(player, Attribute.GRAVITY);
        resetToDefault(player, Attribute.STEP_HEIGHT);
        resetToDefault(player, Attribute.SCALE);
        resetToDefault(player, Attribute.ENTITY_INTERACTION_RANGE);
    }

    private static void resetToDefault(Player player, Attribute attribute) {
        AttributeInstance instance = Objects.requireNonNull(player.getAttribute(attribute));
        instance.setBaseValue(instance.getDefaultValue());
    }

    public static void restrictPlayer(Player player){
        AttributeInstance movementAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementAttr != null) {
            removeModifier(movementAttr, RESTRICT_MOVEMENT_KEY);
            movementAttr.addModifier(new AttributeModifier(
                    RESTRICT_MOVEMENT_KEY,
                    -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            ));
        }

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            removeModifier(jumpAttr, RESTRICT_JUMP_KEY);
            jumpAttr.addModifier(new AttributeModifier(
                    RESTRICT_JUMP_KEY,
                    -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            ));
        }

        AttributeInstance gravityAttr = player.getAttribute(Attribute.GRAVITY);
        if (gravityAttr != null) {
            removeModifier(gravityAttr, RESTRICT_GRAVITY_KEY);
            gravityAttr.addModifier(new AttributeModifier(
                    RESTRICT_GRAVITY_KEY,
                    2.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1
            ));
        }
    }

    public static void unRestrictPlayer(Player player){
        AttributeInstance movementAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementAttr != null) {
            removeModifier(movementAttr, RESTRICT_MOVEMENT_KEY);
        }

        AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jumpAttr != null) {
            removeModifier(jumpAttr, RESTRICT_JUMP_KEY);
        }

        AttributeInstance gravityAttr = player.getAttribute(Attribute.GRAVITY);
        if (gravityAttr != null) {
            removeModifier(gravityAttr, RESTRICT_GRAVITY_KEY);
        }
    }

    public static boolean isRestricted(Player player){
        AttributeInstance movementAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (movementAttr != null) {
            for (AttributeModifier mod : movementAttr.getModifiers()) {
                if (mod.getKey().equals(RESTRICT_MOVEMENT_KEY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeModifier(AttributeInstance attr, NamespacedKey key) {
        attr.getModifiers().stream()
                .filter(mod -> mod.getKey().equals(key))
                .forEach(attr::removeModifier);
    }
}
