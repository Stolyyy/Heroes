package me.stolyy.heroes.heros.configs;

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
) {
    public Object getProperty(String key){
        return properties().get(key);
    }

    public Object getVisual(String key){
        return visuals().get(key);
    }

    public String getSound(String key){
        return sounds().get(key);
    }
}
