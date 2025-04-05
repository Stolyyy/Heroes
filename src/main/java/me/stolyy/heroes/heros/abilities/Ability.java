package me.stolyy.heroes.heros.abilities;

public class Ability {
    public AbilityType abilityType;
    public boolean ready;
    public double dmg;
    public double kb;
    public double cd;
    public double timeUntilUse;

    public Ability(AbilityType abilityType, boolean ready, double dmg, double kb, double cd) {
        this.abilityType = abilityType;
        this.ready = ready;
        this.dmg = dmg;
        this.kb = kb;
        timeUntilUse = this.cd = cd;
    }
}
