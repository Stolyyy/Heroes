package me.stolyy.heroes.hero.config;

public class AbilityEnums
{
    public enum AbilityType
    {
        PRIMARY,
        SECONDARY,
        ULTIMATE,
        PASSIVE
    }

    public enum CooldownType
    {
        TIME,
        ENERGY,
        BOTH,
        NONE
    }

    public enum AbilityLogic
    {
        CUSTOM,
        CONE,
        DASH,
        HITSCAN,
        PROJECTILE,
        SHOCKWAVE
    }
}
