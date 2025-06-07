package me.stolyy.heroes.game.minigame;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;

public class GameEnums {

    public enum GameMode {
        ONE_V_ONE,
        TWO_V_TWO,
        PARTY
    }

    public enum GameState {
        WAITING,
        STARTING,
        IN_PROGRESS,
        ENDED
    }

    public enum GameTeam {
        RED(NamedTextColor.RED),
        BLUE(NamedTextColor.BLUE),
        GREEN(NamedTextColor.GREEN),
        YELLOW(NamedTextColor.YELLOW),
        SPECTATOR(NamedTextColor.GRAY);

        private final NamedTextColor chatColor;

        GameTeam(NamedTextColor chatColor) {
            this.chatColor = chatColor;
        }

        public NamedTextColor getChatColor() {
            return chatColor;
        }
    }
}