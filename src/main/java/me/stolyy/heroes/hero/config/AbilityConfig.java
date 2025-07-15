package me.stolyy.heroes.hero.config;

import java.util.Map;

public record AbilityConfig(
    String name,
    String description,
    AbilityEnums.AbilityLogic logic,

    double damage,
    double knockback,
    double duration,
    double cooldown,
    double energyCost,

    AbilityEnums.AbilityType abilityType,
    AbilityEnums.CooldownType cooldownType,

    Map<String, Object> properties,

    Map<String, Object> visuals,
    Map<String, String> sounds
) { }
