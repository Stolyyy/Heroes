package me.stolyy.heroes.game;

public enum GameMode {
    ONE_V_ONE("1v1", 1, 2),
    TWO_V_TWO("2v2", 2, 2),
    CUSTOM("Custom", 8, 8);

    private final String displayName;
    private final int maxPlayersPerTeam;
    private final int maxTeams;

    GameMode(String displayName, int maxPlayersPerTeam, int maxTeams) {
        this.displayName = displayName;
        this.maxPlayersPerTeam = maxPlayersPerTeam;
        this.maxTeams = maxTeams;
    }

    public String displayName() { return displayName; }
    public int maxPlayersPerTeam() { return maxPlayersPerTeam; }
    public int maxTeams() { return maxTeams; }
    public int maxTotalPlayers() { return maxPlayersPerTeam * maxTeams; }

    public boolean hasElo() {
        return this == ONE_V_ONE || this == TWO_V_TWO;
    }
}
