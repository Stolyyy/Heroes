package me.stolyy.heroes.heros.bots;

import me.stolyy.heroes.heros.bots.strategies.Strategy;
import org.bukkit.Location;

public abstract class Bot {
    //Character character
    // ^ refers to minion/hero
    private Strategy strategy;

    public abstract void summon(Location location);
    public abstract void clean();
}
