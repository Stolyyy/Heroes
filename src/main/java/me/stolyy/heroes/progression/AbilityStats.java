package me.stolyy.heroes.progression;

public class AbilityStats {
    private int kills;
    private double damage;
    private double voidDamage;

    public AbilityStats() {}

    public AbilityStats(int kills, double damage, double voidDamage) {
        this.kills = kills;
        this.damage = damage;
        this.voidDamage = voidDamage;
    }

    public void addKill() { kills++; }
    public void addDamage(double amount) { damage += amount; }
    public void addVoidDamage(double amount) { voidDamage += amount; }

    public int kills() { return kills; }
    public double damage() { return damage; }
    public double voidDamage() { return voidDamage; }

    public void setKills(int kills) { this.kills = kills; }
    public void setDamage(double damage) { this.damage = damage; }
    public void setVoidDamage(double voidDamage) { this.voidDamage = voidDamage; }

    public AbilityStats combine(AbilityStats other) {
        return new AbilityStats(
            kills + other.kills,
            damage + other.damage,
            voidDamage + other.voidDamage
        );
    }
}
