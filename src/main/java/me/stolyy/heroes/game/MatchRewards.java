package me.stolyy.heroes.game;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.EloCalculator;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.ui.visual.HeroNpcSkins;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks per-match XP, handles level-ups and tokens, and commits end-of-game W/L and ELO. */
final class MatchRewards {
    private static final String MASTER_SKIN_ID = "masterskin";

    private final Game game;
    private final Map<UUID, Map<String, Integer>> xpEarned = new HashMap<>();

    MatchRewards(Game game) {
        this.game = game;
    }

    void initializePlayerStats(Player player, String heroName) {
        if (heroName == null) {
            throw new IllegalStateException("Cannot initialize player stats for " + player.getName() + " without a hero");
        }
        PlayerStatsManager.getStats(player).setCurrentContext(game.gameMode(), heroName);
    }

    void awardKillXp(Player killer) {
        awardXp(killer, scaleXp(ProgressionConfig.getKillReward()));
    }

    void awardXp(Player player, int amount) {
        if (amount <= 0) return;
        PlayerStats stats = PlayerStatsManager.getStats(player);
        String heroName = stats.currentHero();
        if (heroName == null) {
            throw new IllegalStateException("Cannot award XP to " + player.getName() + ": currentHero context is not set");
        }
        xpEarned.computeIfAbsent(player.getUniqueId(), k -> new LinkedHashMap<>())
                .merge(heroName, amount, Integer::sum);
        int oldSmashLevel = stats.smashLevel();
        List<Integer> newLevels = stats.getHeroLevel(heroName).addXp(amount);
        handleLevelUps(player, heroName, newLevels, stats);
        grantSmashLevelTokens(player, stats, oldSmashLevel);
    }

    void recordWinLoss(@Nullable Team winningTeam) {
        for (Map.Entry<UUID, TeamColor> entry : game.originalTeamMap().entrySet()) {
            UUID uuid = entry.getKey();
            if (game.isBotParticipant(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            PlayerStats stats = p != null ? PlayerStatsManager.getStats(p) : PlayerStatsManager.getStats(uuid);
            boolean won = winningTeam != null && entry.getValue() == winningTeam.color();
            if (won) stats.recordWin();
            else stats.recordLoss();

            if (p == null) continue;
            boolean present = !game.getGamePlayer(uuid).leftGame();
            awardXp(p, scaleXp((present ? ProgressionConfig.getGameReward() : 0)
                    + (won ? ProgressionConfig.getWinReward() : 0)));
        }

        sendPersonalStats();
        sendXpSummaries();

        if (game.gameMode().hasElo() && winningTeam != null) {
            updateElo(winningTeam);
        }

        for (UUID uuid : game.originalTeamMap().keySet()) {
            PlayerStatsManager.saveStats(uuid);
        }
    }

    private int scaleXp(int amount) {
        if (game.gameMode() != GameMode.CUSTOM) return amount;
        return (int) Math.round(amount * ProgressionConfig.getCustomXpMultiplier());
    }

    private void grantSmashLevelTokens(Player player, PlayerStats stats, int oldSmashLevel) {
        int cadence = ProgressionConfig.getTokenEveryNLevels();
        if (cadence <= 0) return;
        int earned = stats.smashLevel() / cadence - oldSmashLevel / cadence;
        if (earned <= 0) return;
        stats.addTokens(earned);
        player.sendMessage(Component.text(
                (earned == 1 ? "You earned an unlock token!" : "You earned " + earned + " unlock tokens!")
                        + " Balance: " + stats.getTokenBalance(), NamedTextColor.GOLD));
    }

    private void sendPersonalStats() {
        for (Map.Entry<UUID, TeamColor> entry : game.originalTeamMap().entrySet()) {
            if (entry.getValue() == TeamColor.SPECTATOR) continue;
            GamePlayer gamePlayer = game.getGamePlayer(entry.getKey());
            if (gamePlayer.isBot() || gamePlayer.leftGame()) continue;
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            game.visuals().sendPersonalStats(p, gamePlayer, xpEarned.get(entry.getKey()));
        }
    }

    private void sendXpSummaries() {
        for (Map.Entry<UUID, Map<String, Integer>> entry : xpEarned.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;
            PlayerStats stats = PlayerStatsManager.getStats(p);
            for (Map.Entry<String, Integer> heroXp : entry.getValue().entrySet()) {
                HeroLevel level = stats.getHeroLevel(heroXp.getKey());
                int required = ProgressionConfig.getXpForLevel(level.level());
                String heroDisplay = HeroConfig.formatName(heroXp.getKey());
                if (required == Integer.MAX_VALUE) {
                    p.sendMessage(Component.text("You earned " + heroXp.getValue() + " XP on "
                            + heroDisplay + ". Level " + level.level() + " (MAX).", NamedTextColor.AQUA));
                } else {
                    int pct = (int) Math.round(100.0 * level.xp() / required);
                    p.sendMessage(Component.text("You earned " + heroXp.getValue() + " XP on "
                            + heroDisplay + ". You are " + level.xp() + "/" + required + " (" + pct
                            + "%) of the way to level " + (level.level() + 1) + ".", NamedTextColor.AQUA));
                }
            }
        }
    }

    private void handleLevelUps(Player player, String heroName, List<Integer> newLevels, PlayerStats stats) {
        for (int level : newLevels) {
            player.sendMessage(Component.text(HeroConfig.formatName(heroName) + " reached level " + level + "!",
                    NamedTextColor.AQUA));
            if (level == ProgressionConfig.getMasterSkinLevel()) {
                stats.setSkinForHero(heroName, MASTER_SKIN_ID);
                HeroNpcSkins.refresh(player, heroName);
                player.sendMessage(Component.text("You mastered " + HeroConfig.formatName(heroName)
                        + " — Master Skin unlocked!", NamedTextColor.LIGHT_PURPLE));
            }
        }
    }

    private void updateElo(Team winningTeam) {
        Map<TeamColor, List<UUID>> teamMembers = new HashMap<>();
        for (Map.Entry<UUID, TeamColor> entry : game.originalTeamMap().entrySet()) {
            if (game.isBotParticipant(entry.getKey())) continue;
            teamMembers.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        List<UUID> winners = teamMembers.getOrDefault(winningTeam.color(), List.of());
        if (winners.isEmpty()) return;
        List<Integer> winnerRatings = ratings(winners);

        for (Map.Entry<TeamColor, List<UUID>> entry : teamMembers.entrySet()) {
            if (entry.getKey() == winningTeam.color() || entry.getKey() == TeamColor.SPECTATOR) continue;
            List<UUID> losers = entry.getValue();
            var result = EloCalculator.calculate(game.gameMode(), winnerRatings, ratings(losers));
            for (UUID uuid : winners) applyElo(uuid, result.winnerChange());
            for (UUID uuid : losers) applyElo(uuid, result.loserChange());
        }
    }

    private List<Integer> ratings(List<UUID> uuids) {
        List<Integer> result = new ArrayList<>(uuids.size());
        for (UUID uuid : uuids) result.add(PlayerStatsManager.getStats(uuid).getElo(game.gameMode()));
        return result;
    }

    private void applyElo(UUID uuid, int change) {
        PlayerStats stats = PlayerStatsManager.getStats(uuid);
        int oldElo = stats.getElo(game.gameMode());
        stats.addElo(game.gameMode(), change);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) sendEloChange(p, oldElo, stats.getElo(game.gameMode()));
    }

    private void sendEloChange(Player p, int oldElo, int newElo) {
        int delta = newElo - oldElo;
        NamedTextColor deltaColor = delta >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED;
        String sign = delta >= 0 ? "+" : "";
        p.sendMessage(Component.text("Elo: ", NamedTextColor.GRAY)
                .append(Component.text(oldElo + " → " + newElo, NamedTextColor.WHITE))
                .append(Component.text(" (" + sign + delta + ")", deltaColor)));
    }
}
