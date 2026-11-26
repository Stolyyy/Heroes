package me.stolyy.heroes.game.settings;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.game.map.GameMap;
import org.bukkit.util.BoundingBox;

public class GameSettings {
    private final GameMap map;
    private BoundingBox boundaries;
    private boolean killWalls;
    private boolean smashCrystals;
    private int timer;
    private int crystalInterval = GameConfigManager.getCrystalDefaultInterval();

    public GameSettings(GameMap map) {
        this.map = map;
        boundaries = map.boundaries().clone();

        this.timer = Configs.requireInt(GameConfigManager.getGameConfig(), "defaults.game.timer");
        this.killWalls = Configs.requireBoolean(GameConfigManager.getGameConfig(), "defaults.game.kill-walls");
        this.smashCrystals = Configs.requireBoolean(GameConfigManager.getGameConfig(), "defaults.game.smash-crystals");
        if (killWalls) {
            boundaries = map.boundaries().clone().expand(-3);
        }
    }

    public GameSettings copy(GameSettings settings){
        setSmashCrystals(settings.smashCrystals());
        setTimer(settings.timer());
        setCrystalInterval(settings.crystalInterval());
        if(this.killWalls != settings.killWalls()) toggleKillWalls();
        return this;
    }

    public GameMap map() {
        return map;
    }

    public boolean smashCrystals() {
        return smashCrystals;
    }

    public GameSettings setSmashCrystals(boolean smashCrystals) {
        this.smashCrystals = smashCrystals;
        return this;
    }

    public int timer() {
        return timer;
    }

    public GameSettings setTimer(int timer) {
        this.timer = timer;
        return this;
    }

    public int crystalInterval() {
        return crystalInterval;
    }

    public void setCrystalInterval(int interval) {
        this.crystalInterval = interval;
    }

    public BoundingBox boundaries() {
        return boundaries;
    }

    public boolean killWalls(){
        return killWalls;
    }

    public GameSettings toggleKillWalls() {
        killWalls = !killWalls;
        if(killWalls){
            boundaries = map.boundaries().clone().expand(-3);
        } else {
            boundaries = map.boundaries().clone();
        }
        return this;
    }
}
