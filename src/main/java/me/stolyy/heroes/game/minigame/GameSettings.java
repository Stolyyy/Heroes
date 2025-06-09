package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.game.maps.GameMap;

//Overall: crystals, map,
public class GameSettings {
    private final GameMap map;
    private boolean smashCrystals;
    private int timer = 420; //seconds

    public GameSettings(GameMap map) {
        this.map = map;
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
}
