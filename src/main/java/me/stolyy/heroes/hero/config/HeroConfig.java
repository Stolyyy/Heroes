package me.stolyy.heroes.hero.config;

import me.stolyy.heroes.hero.data.AbilityData;

import java.util.Map;

public record HeroConfig(
        String name,
        String description,
        HeroType type,

        Map<String, AbilityConfig> abilityConfigs,

        String headName,
        String chestName,
        String legsName,
        String feetName,

        String primaryItemModel,
        String head,
        int[] chestColors, //RGB
        int[] legsColors, //RGB
        int[] feetColors, //RGB

        double maxHealth,
        double weight,
        double maxDoubleJumps,
        double walkSpeed,
        double jumpHeight,
        double gravity,

        double maxEnergy,
        double energyRegen,

        double jabDamage,
        double jabKnockback,
        double jabCooldown,
        double jabReach,
        String jabHitSound,
        String jabSwingSound,

        int maxAmmo
        ) { }
