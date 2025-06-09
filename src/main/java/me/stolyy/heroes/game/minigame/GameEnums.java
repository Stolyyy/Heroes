package me.stolyy.heroes.game.minigame;

import net.kyori.adventure.text.format.NamedTextColor;

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

    public enum TeamColor {
        RED(NamedTextColor.RED),
        BLUE(NamedTextColor.BLUE),
        GREEN(NamedTextColor.GREEN),
        YELLOW(NamedTextColor.YELLOW),
        SPECTATOR(NamedTextColor.GRAY);

        private final NamedTextColor chatColor;

        TeamColor(NamedTextColor chatColor) {
            this.chatColor = chatColor;
        }

        public NamedTextColor chatColor() {
            return chatColor;
        }
    }
}