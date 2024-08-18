package me.stolyy.heroes.heros;

import me.stolyy.heroes.Hero;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class HeroManager {
    private Map<Player, Hero> playerCharacters = new HashMap<>();

    public void setPlayerCharacter(Player player, Hero hero) {
        playerCharacters.put(player, hero);
    }

    public Hero getPlayerCharacter(Player player) {
        return playerCharacters.get(player);
    }
}
