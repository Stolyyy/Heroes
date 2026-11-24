package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;

import java.util.List;

/**
 * When a game is over, and who won it. Dispatch is polymorphic on the enum —
 * no per-mode Game subclasses, no god-switch.
 */
public enum WinCondition {

    /** Classic: the game ends when at most one team still has active players. */
    LAST_TEAM_STANDING {
        @Override
        public boolean isMet(Game game) {
            return activeTeams(game).size() <= 1;
        }

        @Override
        public Team winningTeam(Game game) {
            List<Team> active = activeTeams(game);
            return active.size() == 1 ? active.get(0) : null;
        }
    },

    /**
     * TDM: first team to {@link GameModeConfig#killLimit()} kills wins.
     * Last-team-standing stays as a fallback so a rage-quit still ends the game.
     */
    KILL_TARGET {
        @Override
        public boolean isMet(Game game) {
            return reachedKillTarget(game) != null || LAST_TEAM_STANDING.isMet(game);
        }

        @Override
        public Team winningTeam(Game game) {
            Team byKills = reachedKillTarget(game);
            return byKills != null ? byKills : LAST_TEAM_STANDING.winningTeam(game);
        }

        @Override
        public Team timeoutWinner(Game game) {
            TeamColor leader = leadingTeamByKills(game);
            return leader != null ? game.getTeams().get(leader) : null;
        }
    },

    /**
     * Evolution: the win fires inside {@link OnKillAction#ADVANCE_HERO} (a kill on the
     * final hero ends the game directly) — this condition only supplies the
     * last-team-standing fallback.
     */
    HERO_EXHAUSTION {
        @Override
        public boolean isMet(Game game) {
            return LAST_TEAM_STANDING.isMet(game);
        }

        @Override
        public Team winningTeam(Game game) {
            return LAST_TEAM_STANDING.winningTeam(game);
        }

        @Override
        public Team timeoutWinner(Game game) {
            // Furthest through their hero order wins at the bell; cross-team tie = draw.
            int best = -1;
            TeamColor leader = null;
            boolean tie = false;
            for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
                if (gamePlayer.leftGame() || gamePlayer.heroOrder() == null) continue;
                if (gamePlayer.heroIndex() > best) {
                    best = gamePlayer.heroIndex();
                    leader = gamePlayer.team();
                    tie = false;
                } else if (gamePlayer.heroIndex() == best && gamePlayer.team() != leader) {
                    tie = true;
                }
            }
            return (leader != null && !tie) ? game.getTeams().get(leader) : null;
        }
    };

    /** Whether the game should end right now. */
    public abstract boolean isMet(Game game);

    /** The winning team once {@link #isMet} holds, or null when there is none (draw). */
    public abstract Team winningTeam(Game game);

    /** Winner when the match timer expires; null = draw. Default: most lives remaining. */
    public Team timeoutWinner(Game game) {
        return calculateWinnerByLives(game);
    }

    /** Team with the most total remaining lives, or null on a tie / empty board. */
    public static Team calculateWinnerByLives(Game game) {
        Team winningTeam = null;
        int maxLives = -1;
        boolean isDraw = false;

        for (Team team : game.getTeams().values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;

            int totalLives = team.playerUUIDs().stream()
                    .mapToInt(uuid -> game.getGamePlayer(uuid).currentLives())
                    .sum();
            if (totalLives > maxLives) {
                maxLives = totalLives;
                winningTeam = team;
                isDraw = false;
            } else if (totalLives == maxLives) {
                isDraw = true;
            }
        }
        return (isDraw || winningTeam == null) ? null : winningTeam;
    }

    /** Total kills for a team, including participants who have since left the game. */
    public static int teamKills(Game game, TeamColor color) {
        int total = 0;
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.team() == color) total += gamePlayer.kills();
        }
        return total;
    }

    /** Team with the most kills, or null on a tie (KILL_TARGET timeout winner). */
    public static TeamColor leadingTeamByKills(Game game) {
        TeamColor leader = null;
        int best = -1;
        boolean tie = false;
        for (TeamColor color : TeamColor.values()) {
            if (color == TeamColor.SPECTATOR) continue;
            int kills = teamKills(game, color);
            if (kills > best) {
                best = kills;
                leader = color;
                tie = false;
            } else if (kills == best) {
                tie = true;
            }
        }
        return tie ? null : leader;
    }

    private static List<Team> activeTeams(Game game) {
        return game.getTeams().values().stream()
                .filter(t -> t.color() != TeamColor.SPECTATOR)
                .filter(t -> t.playerUUIDs().stream().anyMatch(game.getActivePlayers()::contains))
                .toList();
    }

    private static Team reachedKillTarget(Game game) {
        int limit = game.modeConfig().killLimit();
        if (limit <= 0) return null;
        for (Team team : game.getTeams().values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;
            if (teamKills(game, team.color()) >= limit) return team;
        }
        return null;
    }
}
