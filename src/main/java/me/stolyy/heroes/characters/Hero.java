package me.stolyy.heroes.characters;

import me.stolyy.heroes.characters.abilities.Ability;
import org.bukkit.entity.Player;

public class Hero {
    private final String name;
    private final double maxHealth;
    private final double movementSpeed;
    private final double weight; // For knockback calculation
    
    private final Ability primaryAbility;
    private final Ability secondaryAbility;
    private final Ability smashAbility;
    private final Ability passiveAbility;

    public Hero(String name, double maxHealth, double movementSpeed, double weight,
                Ability primary, Ability secondary, Ability smash, Ability passive) {
        this.name = name;
        this.maxHealth = maxHealth;
        this.movementSpeed = movementSpeed;
        this.weight = weight;
        this.primaryAbility = primary;
        this.secondaryAbility = secondary;
        this.smashAbility = smash;
        this.passiveAbility = passive;
    }

    public String getName() { return name; }
    public double getMaxHealth() { return maxHealth; }
    public double getWeight() { return weight; }

    public void usePrimary(Player player) {
        // Logic to check cooldowns and energy would go here or in a CooldownManager
        primaryAbility.execute(player);
    }

    public void useSecondary(Player player) {
        secondaryAbility.execute(player);
    }
    
    public void useSmash(Player player) {
        smashAbility.execute(player);
    }

    public void applyPassive(Player player) {
        if (passiveAbility != null) {
            passiveAbility.execute(player);
        }
    }
}