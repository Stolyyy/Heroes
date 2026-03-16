package me.stolyy.heroes.heros.configs;

import java.util.Map;

public record HeroConfig(
        String name,
        String description,
        HeroType type,

        double maxHealth,
        double weight,

        Map<String, AbilityConfig> abilityConfigs,

        EquipmentConfig equipmentConfig,
        MovementConfig movementConfig,
        EnergyConfig energyConfig,
        JabConfig jabConfig,
        AmmoConfig ammoConfig
) { }

