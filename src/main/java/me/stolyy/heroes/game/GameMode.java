package me.stolyy.heroes.game;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum GameMode {
    ONE_V_ONE("1v1", "1v1", 1, 2),
    TWO_V_TWO("2v2", "2v2", 2, 2),
    CUSTOM("Custom", "custom", 8, 8);

    private final String displayName;
    private final String token;
    private final int maxPlayersPerTeam;
    private final int maxTeams;

    GameMode(String displayName, String token, int maxPlayersPerTeam, int maxTeams) {
        this.displayName = displayName;
        this.token = token;
        this.maxPlayersPerTeam = maxPlayersPerTeam;
        this.maxTeams = maxTeams;
    }

    public String displayName() { return displayName; }
    public String token() { return token; }
    public int maxPlayersPerTeam() { return maxPlayersPerTeam; }
    public int maxTeams() { return maxTeams; }
    public int maxTotalPlayers() { return maxPlayersPerTeam * maxTeams; }

    public boolean hasElo() {
        return this == ONE_V_ONE || this == TWO_V_TWO;
    }

    public static List<String> tokens() {
        return List.of("1v1", "2v2", "custom");
    }

    public static List<String> rankedTokens() {
        return List.of("1v1", "2v2");
    }

    public static Optional<GameMode> fromToken(String input) {
        if (input == null) return Optional.empty();
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "1v1", "one_v_one" -> Optional.of(ONE_V_ONE);
            case "2v2", "two_v_two" -> Optional.of(TWO_V_TWO);
            case "custom", "party" -> Optional.of(CUSTOM);
            default -> Optional.empty();
        };
    }
}
