package me.stolyy.heroes.game;

import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure domain logic for ordering custom-game rosters and auto-balancing teams by session score and ELO. */
public final class TeamBalancer {

    public enum Shape {
        FFA("ffa"),
        TWO_TEAMS("two-teams"),
        TEAMS_OF_TWO("teams-of-two");

        private final String configKey;

        Shape(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }

        public Shape next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private TeamBalancer() {}

    /** Online players sorted A-Z, followed by game bots sorted A-Z. */
    public static List<UUID> orderedMembers(Game game, Team team) {
        Map<UUID, GameBot> bots = game.getGameBots();
        List<UUID> players = new ArrayList<>();
        List<UUID> botIds = new ArrayList<>();
        for (UUID uuid : team.playerUUIDs()) {
            if (Bukkit.getPlayer(uuid) != null) players.add(uuid);
            else if (bots.containsKey(uuid)) botIds.add(uuid);
        }
        players.sort(Comparator.comparing(u -> Bukkit.getPlayer(u).getName()));
        botIds.sort(Comparator.comparing(u -> bots.get(u).getName()));
        players.addAll(botIds);
        return players;
    }

    /** Every active participant (online players and bots), spectators included. */
    public static List<UUID> participants(Game game) {
        List<UUID> all = new ArrayList<>();
        for (Team team : game.getTeams().values()) {
            all.addAll(orderedMembers(game, team));
        }
        return all;
    }

    public static int eloOf(Game game, UUID uuid) {
        if (game.getGameBots().containsKey(uuid)) return 1000;
        PlayerStats stats = PlayerStatsManager.getStats(uuid);
        return (stats.getElo(GameMode.ONE_V_ONE) + stats.getElo(GameMode.TWO_V_TWO)) / 2;
    }

    /** Computes team sizes for up to 8 teams of 2 (max 16 seated players). */
    public static List<Integer> teamSizesOfTwo(int n) {
        List<Integer> sizes = new ArrayList<>();
        int remaining = Math.min(n, 16);
        while (remaining > 0 && sizes.size() < 8) {
            sizes.add(Math.min(2, remaining));
            remaining -= 2;
        }
        return sizes;
    }

    /** Applies the selected team balance shape across all participants. Returns false if empty. */
    public static boolean apply(Game game, Shape shape) {
        List<UUID> parts = participants(game);
        if (parts.isEmpty()) return false;

        Map<UUID, GameBot> bots = game.getGameBots();
        Set<UUID> lobbyHumans = new HashSet<>();
        for (UUID uuid : parts) {
            if (!bots.containsKey(uuid)) lobbyHumans.add(uuid);
        }
        Map<UUID, Integer> sessionScores = MatchLog.sessionScores(lobbyHumans);

        parts.sort(Comparator
                .comparingInt((UUID u) -> sessionScores.getOrDefault(u, 0))
                .thenComparingInt((UUID u) -> eloOf(game, u))
                .reversed());

        TeamColor[] order = Arrays.stream(TeamColor.values())
                .filter(c -> c != TeamColor.SPECTATOR)
                .toArray(TeamColor[]::new);

        Map<UUID, TeamColor> target = new HashMap<>();
        switch (shape) {
            case FFA -> {
                for (int i = 0; i < parts.size(); i++) {
                    target.put(parts.get(i), i < order.length ? order[i] : TeamColor.SPECTATOR);
                }
            }
            case TWO_TEAMS -> {
                // Snake draft (R, B, B, R, ...) to keep team average ratings balanced.
                for (int i = 0; i < parts.size(); i++) {
                    boolean red = i % 4 == 0 || i % 4 == 3;
                    target.put(parts.get(i), red ? order[0] : order[1]);
                }
            }
            case TEAMS_OF_TWO -> {
                int lo = 0, hi = parts.size() - 1, team = 0;
                while (lo <= hi && team < order.length) {
                    target.put(parts.get(lo), order[team]);
                    if (hi > lo) target.put(parts.get(hi), order[team]);
                    lo++;
                    hi--;
                    team++;
                }
                for (; lo <= hi; lo++) target.put(parts.get(lo), TeamColor.SPECTATOR);
            }
        }

        target.forEach(game::changeParticipantTeam);
        return true;
    }
}
