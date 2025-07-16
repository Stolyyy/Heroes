package me.stolyy.heroes.hero.configs;

import java.util.Map;

public record AbilityConfig(
    String name,
    String description,
    AbilityEnums.AbilityLogicType logicType,

    double damage,
    double knockback,
    double duration,
    double cooldown,
    double energyCost,

    AbilityEnums.AbilityType abilityType,
    AbilityEnums.CooldownType cooldownType,
    AbilityEnums.CooldownDisplay cooldownDisplay,
    AbilityEnums.CooldownReadyDisplay cooldownReadyDisplay,

    Map<String, Object> properties,

    Map<String, Object> visuals,
    Map<String, String> sounds
) { }
