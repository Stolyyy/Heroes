package me.stolyy.heroes.heros.abilities;

public class Ability {
    public AbilityType abilityType;
    public boolean ready;
    public boolean inUse;
    public double dmg;
    public double kb;
    public double cd;
    public double timeUntilUse;
    public double duration;

    public Ability(AbilityType abilityType, double dmg, double kb, double cd) {
        this.abilityType = abilityType;
        ready = !abilityType.equals(AbilityType.ULTIMATE);
        inUse = false;
        this.dmg = dmg;
        this.kb = kb;
        timeUntilUse = this.cd = cd;
        duration = 0;
    }

    public Ability(AbilityType abilityType, double dmg, double kb, double cd, double duration) {
        this.abilityType = abilityType;
        ready = !abilityType.equals(AbilityType.ULTIMATE);
        inUse = false;
        this.dmg = dmg;
        this.kb = kb;
        timeUntilUse = this.cd = cd;
        this.duration = duration;
    }
}
