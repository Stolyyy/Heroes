package me.stolyy.heroes.Games;

public class GameSettingsManager {
    private boolean friendlyFire;
    private boolean randomHeroes;
    private boolean smashCrystals;
    private boolean ultimates;

    public GameSettingsManager() {
        // Default settings
        this.friendlyFire = false;
        this.randomHeroes = false;
        this.smashCrystals = false;
        this.ultimates = true;
    }

    public boolean isFriendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public boolean isRandomHeroes() {
        return randomHeroes;
    }

    public void setRandomHeroes(boolean randomHeroes) {
        this.randomHeroes = randomHeroes;
    }

    public boolean isSmashCrystals() {
        return smashCrystals;
    }

    public void setSmashCrystals(boolean smashCrystals) {
        this.smashCrystals = smashCrystals;
    }

    public boolean isUltimates() {
        return ultimates;
    }

    public void setUltimates(boolean ultimates) {
        this.ultimates = ultimates;
    }

    public void updateFrom(GameSettingsManager other) {
        this.friendlyFire = other.friendlyFire;
        this.randomHeroes = other.randomHeroes;
        this.smashCrystals = other.smashCrystals;
        this.ultimates = other.ultimates;
    }
}