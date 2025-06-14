package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.game.maps.GameMap;
import org.bukkit.util.BoundingBox;

//Overall: crystals, map,
public class GameSettings {
    private final GameMap map;
    private BoundingBox boundaries;
    private boolean killWalls;
    private boolean smashCrystals;
    private int timer = 420; //seconds

    public GameSettings(GameMap map) {
        this.map = map;
        boundaries = map.boundaries().clone();
    }

    public GameSettings copy(GameSettings settings){
        setSmashCrystals(settings.smashCrystals());
        setTimer(settings.timer());
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

    public BoundingBox boundaries() {
        return boundaries;
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
