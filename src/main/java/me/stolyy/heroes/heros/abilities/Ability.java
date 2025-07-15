package me.stolyy.heroes.heros.abilities;

import me.stolyy.heroes.hero.config.AbilityType;
import me.stolyy.heroes.heros.abilities.data.AbilityData;

public class Ability {
    private AbilityType abilityType;
    private boolean ready, inUse;
    private double dmg, kb, cd, timeUntilUse, duration;
    private AbilityData abilityData;

    public Ability(AbilityType abilityType, double dmg, double kb, double cd) {
        this.abilityType = abilityType;
        ready = !abilityType.equals(AbilityType.ULTIMATE);
        inUse = false;
        this.dmg = dmg;
        this.kb = kb;
        timeUntilUse = this.cd = cd;
        duration = 0;
    }
    public Ability(AbilityType abilityType, double dmg, double kb, double cd, AbilityData abilityData) {
        this.abilityType = abilityType;
        ready = !abilityType.equals(AbilityType.ULTIMATE);
        inUse = false;
        this.dmg = dmg;
        this.kb = kb;
        timeUntilUse = this.cd = cd;
        duration = 0;
        this.abilityData = abilityData;
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
    public Ability(AbilityType abilityType, double dmg, double kb, double cd, double duration, AbilityData abilityData) {
        this.abilityType = abilityType;
        ready = !abilityType.equals(AbilityType.ULTIMATE);
        inUse = false;
        this.dmg = dmg;
        this.kb = kb;
        timeUntilUse = this.cd = cd;
        this.duration = duration;
        this.abilityData = abilityData;
    }

    public void cooldown() {
        ready = false;
        timeUntilUse = cd;
        inUse = false;
    }

    public void updateCooldown(double deltaSeconds) {
        if (ready) return;

        timeUntilUse = Math.max(0, timeUntilUse - deltaSeconds);
        if (timeUntilUse <= 0) {
            ready = true;
            timeUntilUse = 0;
            if(abilityType.equals(AbilityType.ULTIMATE)) {
                inUse = false;
            }
        }
    }

    public AbilityType abilityType() {
        return abilityType;
    }
    public Ability setAbilityType(AbilityType abilityType) {
        this.abilityType = abilityType;
        return this;
    }
    public boolean ready() {
        return ready;
    }
    public Ability setReady(boolean ready) {
        this.ready = ready;
        return this;
    }
    public boolean inUse() {
        return inUse;
    }
    public Ability setInUse(boolean inUse) {
        this.inUse = inUse;
        return this;
    }
    public double dmg() {
        return dmg;
    }
    public Ability setDmg(double dmg) {
        this.dmg = dmg;
        return this;
    }
    public double kb() {
        return kb;
    }
    public Ability setKb(double kb) {
        this.kb = kb;
        return this;
    }
    public double cd() {
        return cd;
    }
    public Ability setCd(double cd) {
        this.cd = cd;
        return this;
    }
    public double timeUntilUse() {
        return timeUntilUse;
    }
    public Ability setTimeUntilUse(double timeUntilUse) {
        this.timeUntilUse = timeUntilUse;
        return this;
    }
    public double duration() {
        return duration;
    }
    public Ability setDuration(double duration) {
        this.duration = duration;
        return this;
    }
    public AbilityData abilityData() {
        return abilityData;
    }
    public Ability setAbilityData(AbilityData abilityData) {
        this.abilityData = abilityData;
        return this;
    }
}
