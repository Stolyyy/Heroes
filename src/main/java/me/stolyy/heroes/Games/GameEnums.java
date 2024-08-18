package me.stolyy.heroes.Games;

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
        RED(ChatColor.RED),
        BLUE(ChatColor.BLUE),
        GREEN(ChatColor.GREEN),
        YELLOW(ChatColor.YELLOW);

        private final ChatColor chatColor;

        GameTeam(ChatColor chatColor) {
            this.chatColor = chatColor;
        }

        public ChatColor getChatColor() {
            return chatColor;
        }
    }
}