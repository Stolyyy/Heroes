package me.stolyy.heroes.game.settings;

import me.stolyy.heroes.util.GameConfigManager;

public class PlayerSettings {
    private int lives = GameConfigManager.getInt("defaults.team.lives", 3);
    // Absolute starting/max health (not a bonus on top of the hero base). Range 0-1000.
    private double health = GameConfigManager.getGameConfig().getDouble("defaults.team.health", 100.0);
    private boolean friendlyFire = GameConfigManager.getBoolean("defaults.team.friendly-fire");
    private boolean ultimatesEnabled = GameConfigManager.getGameConfig().getBoolean("defaults.team.ultimates-enabled", true);
    private boolean randomizeHeroes = GameConfigManager.getBoolean("defaults.team.randomize-heroes");

    public PlayerSettings() { }

    /** Copy constructor — every GamePlayer gets its own independent settings object. */
    public PlayerSettings(PlayerSettings source) {
        copy(source);
    }

    public int lives() {
        return lives;
    }

    public PlayerSettings copy(PlayerSettings settings){
        setLives(settings.lives());
        setHealth(settings.health());
        setFriendlyFire(settings.friendlyFire());
        setUltimatesEnabled(settings.ultimatesEnabled());
        setRandomizeHeroes(settings.randomizeHeroes());
        return this;
    }

    public PlayerSettings setLives(int lives) {
        this.lives = lives;
        return this;
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public PlayerSettings setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
        return this;
    }

    public double health() {
        return health;
    }

    public PlayerSettings setHealth(double health) {
        this.health = health;
        return this;
    }

    public boolean ultimatesEnabled() {
        return ultimatesEnabled;
    }

    public PlayerSettings setUltimatesEnabled(boolean ultimatesEnabled) {
        this.ultimatesEnabled = ultimatesEnabled;
        return this;
    }

    public boolean randomizeHeroes() {
        return randomizeHeroes;
    }

    public PlayerSettings setRandomizeHeroes(boolean randomizeHeroes) {
        this.randomizeHeroes = randomizeHeroes;
        return this;
    }
}
