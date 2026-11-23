package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.TeamColor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Boss-mode per-player setup (B13.2). Writes directly into GamePlayers — no override
 * maps: the boss gets round(enemies * livesPerEnemy) lives and bossHpPerLife starting
 * health (via settings, so start()'s seedLives and GameEffects pick them up) plus ults
 * per config; every other participant gets nonBossLives and a flat nonBossHp starting
 * health. Enemy count is fixed at APPLY time — re-designate the boss or re-save the GUI
 * to recompute after roster changes. Everything stays editable through the normal
 * player-settings GUIs.
 *
 * <p>Which player IS the boss is not decided here — the GUI sets it (by head click or by
 * random roll) and passes it in.
 */
public final class BossSetup {

    private BossSetup() {
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
                // Absolute starting/max HP, re-applied by GameEffects on every spawn — i.e.
                // exactly "the HP the boss gets per life".
                gamePlayer.settings().setHealth(bossHpPerLife);
                gamePlayer.settings().setUltimatesEnabled(bossUlts);
            } else {
                gamePlayer.settings().setLives(nonBossLives);
                // Direct settings write (no more start-time override): later per-player GUI
                // edits genuinely win, honoring the "stays editable" contract above.
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

    /**
     * Boss lives = enemies * livesPerEnemy, which is a DECIMAL multiplier: 1.5 with 4 enemies
     * is 6 lives. A fractional result rounds to the NEAREST whole life with ties going up
     * (Math.round), so 1.5 * 3 = 4.5 becomes 5. Nearest keeps the total closest to the
     * configured multiplier at every roster size, and a tie landing in the boss's favour is
     * the safer side to miss on for one player facing a crowd. Never below 1.
     */
    public static int bossLives(int enemyCount, double livesPerEnemy) {
        return Math.max(1, (int) Math.round(enemyCount * livesPerEnemy));
    }

    /**
     * Picks a random participant to be the boss, skipping leavers and spectators. When there is
     * more than one candidate the CURRENT boss is excluded, so the roll always visibly changes
     * something rather than silently landing on whoever is already the boss.
     */
    public static UUID rollBossPlayer(Game game) {
        UUID current = game.bossPlayer();
        List<UUID> pool = new ArrayList<>();
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            pool.add(gamePlayer.uuid());
        }
        if (pool.size() > 1) pool.remove(current);
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }
}
