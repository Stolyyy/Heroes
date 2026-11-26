package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.util.GameModeConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss-mode per-player setup and team designation.
 * Writes directly into {@link GamePlayer} settings so later per-player edits remain possible.
 */
public final class BossSetup {

    public record Config(
            int nonBossLives,
            double nonBossHp,
            double livesPerEnemy,
            double bossHpPerLife,
            boolean ultimatesEnabled) {

        public static Config fromYml() {
            return new Config(
                    GameModeConfigManager.getBossNonBossLives(),
                    GameModeConfigManager.getBossNonBossHp(),
                    GameModeConfigManager.getBossLivesPerEnemy(),
                    GameModeConfigManager.getBossHpPerLife(),
                    GameModeConfigManager.getBossUltimatesEnabled());
        }

        public Config withNonBossLives(int nonBossLives) {
            return new Config(nonBossLives, nonBossHp, livesPerEnemy, bossHpPerLife, ultimatesEnabled);
        }

        public Config withNonBossHp(double nonBossHp) {
            return new Config(nonBossLives, nonBossHp, livesPerEnemy, bossHpPerLife, ultimatesEnabled);
        }

        public Config withLivesPerEnemy(double livesPerEnemy) {
            return new Config(nonBossLives, nonBossHp, livesPerEnemy, bossHpPerLife, ultimatesEnabled);
        }

        public Config withBossHpPerLife(double bossHpPerLife) {
            return new Config(nonBossLives, nonBossHp, livesPerEnemy, bossHpPerLife, ultimatesEnabled);
        }
    }

    private BossSetup() {}

    /**
     * Designates {@code bossUuid} as the boss, places them on {@link TeamColor#RED} and all
     * other active participants on {@link TeamColor#BLUE}, then applies the game's boss config.
     */
    public static void designate(Game game, UUID bossUuid) {
        game.setBossPlayer(bossUuid);
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            TeamColor targetTeam = gamePlayer.uuid().equals(bossUuid) ? TeamColor.RED : TeamColor.BLUE;
            game.changeParticipantTeam(gamePlayer.uuid(), targetTeam);
        }
        apply(game, bossUuid, game.bossConfig());
    }

    /** Re-applies the game's boss config if a boss has already been designated. */
    public static void reapplyIfDesignated(Game game) {
        if (game.bossPlayer() != null) {
            apply(game, game.bossPlayer(), game.bossConfig());
        }
    }

    public static void apply(Game game, UUID bossUuid, Config config) {
        apply(game, bossUuid, config.nonBossLives(), config.nonBossHp(),
                config.livesPerEnemy(), config.bossHpPerLife(), config.ultimatesEnabled());
    }

    public static void apply(Game game, UUID bossUuid,
                             int nonBossLives, double nonBossHp,
                             double livesPerEnemy, double bossHpPerLife,
                             boolean bossUlts) {
        int enemyCount = enemyCount(game, bossUuid);

        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            if (gamePlayer.uuid().equals(bossUuid)) {
                gamePlayer.settings().setLives(bossLives(enemyCount, livesPerEnemy));
                gamePlayer.settings().setHealth(bossHpPerLife);
                gamePlayer.settings().setUltimatesEnabled(bossUlts);
            } else {
                gamePlayer.settings().setLives(nonBossLives);
                gamePlayer.settings().setHealth(nonBossHp);
            }
        }

        game.setBossPlayer(bossUuid);
    }

    /** Live participants not on the boss's team (leavers and spectators excluded). */
    public static int enemyCount(Game game, UUID bossUuid) {
        TeamColor bossTeam = game.getGamePlayer(bossUuid).team();
        int enemyCount = 0;
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            if (gamePlayer.team() != bossTeam) enemyCount++;
        }
        return enemyCount;
    }

    /** Computes boss lives rounded to the nearest whole life (ties up, minimum 1). */
    public static int bossLives(int enemyCount, double livesPerEnemy) {
        return Math.max(1, (int) Math.round(enemyCount * livesPerEnemy));
    }

    /** Formats a whole life multiplier without {@code .0}. */
    public static String formatLives(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * Picks a random participant to be the boss, skipping leavers and spectators and excluding the
     * current boss when another candidate exists. Returns {@code null} if nobody can be boss.
     */
    public static @Nullable UUID rollBossPlayer(Game game) {
        UUID current = game.bossPlayer();
        List<UUID> pool = new ArrayList<>();
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            pool.add(gamePlayer.uuid());
        }
        if (pool.isEmpty()) return null;
        if (pool.size() > 1) pool.remove(current);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
