package me.stolyy.heroes.heros.configs;

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
        CHARGE,
        ENERGY,
        TIME,
        TIME_AND_ENERGY,
    }

    public enum CooldownDisplay
    {
        ACTION_BAR,
        ITEM_DAMAGE,
        NONE
    }

    public enum CooldownReadyDisplay
    {
        ACTION_BAR,
        CHAT,
        TITLE,
        NONE
    }

    public enum AbilityLogicType
    {
        CUSTOM,
        CONE,
        DASH,
        HITSCAN,
        PROJECTILE,
        SHOCKWAVE
    }
}
