package me.stolyy.heroes.game.minigame;

public class TeamSettings {
    private int lives = 3;
    private double maxHealth = 100.0;
    private boolean friendlyFire = false;
    private boolean ultimatesEnabled = true;
    private boolean randomizeHeroes = false;

    public int lives() {
        return lives;
    }

    public TeamSettings copy(TeamSettings settings){
        setLives(settings.lives());
        setMaxHealth(settings.maxHealth());
        setFriendlyFire(settings.friendlyFire());
        setUltimatesEnabled(settings.ultimatesEnabled());
        setRandomizeHeroes(settings.randomizeHeroes());
        return this;
    }

    public TeamSettings setLives(int lives) {
        this.lives = lives;
        return this;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public TeamSettings setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
        return this;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public TeamSettings setMaxHealth(double maxHealth) {
        this.maxHealth = maxHealth;
        return this;
    }

    public boolean ultimatesEnabled() {
        return ultimatesEnabled;
    }

    public TeamSettings setUltimatesEnabled(boolean ultimatesEnabled) {
        this.ultimatesEnabled = ultimatesEnabled;
        return this;
    }

    public boolean randomizeHeroes() {
        return randomizeHeroes;
    }

    public TeamSettings setRandomizeHeroes(boolean randomizeHeroes) {
        this.randomizeHeroes = randomizeHeroes;
        return this;
    }
}
