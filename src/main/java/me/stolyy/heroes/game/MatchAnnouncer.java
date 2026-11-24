package me.stolyy.heroes.game;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Kill feed broadcasts, win-sound representative selection, and post-game summary messages. */
final class MatchAnnouncer {
    private static final int TOP_KILLER_COUNT = 3;

    private final Game game;

    MatchAnnouncer(Game game) {
        this.game = game;
    }

    void broadcastKill(UUID creditId, @Nullable GamePlayer killerGp, String fallbackKillerName, Player victim) {
        String killerName = killerGp != null ? participantName(killerGp) : fallbackKillerName;
        Component killMsg = Component.text(killerName, feedColor(creditId))
                .append(Component.text(" killed ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), feedColor(victim.getUniqueId())));
        for (Player p : game.onlinePlayers()) p.sendMessage(killMsg);
    }

    void broadcastLeaveKill(UUID creditId, GamePlayer killerGp, Player victim) {
        Component msg = Component.text(participantName(killerGp), feedColor(creditId))
                .append(Component.text(" killed ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), feedColor(victim.getUniqueId())))
                .append(Component.text(" (left)", NamedTextColor.GRAY));
        for (Player p : game.onlinePlayers()) p.sendMessage(msg);
    }

    void broadcastDeath(Player victim) {
        Component deathMsg = Component.text(victim.getName(), feedColor(victim.getUniqueId()))
                .append(Component.text(" died", NamedTextColor.GRAY));
        for (Player p : game.onlinePlayers()) p.sendMessage(deathMsg);
    }

    void playWinSound(Team winningTeam) {
        UUID rep = pickWinRepresentative(winningTeam);
        if (rep == null) return;
        Hero repHero = HeroManager.getHero(rep);
        if (repHero == null || repHero.getWinSound() == null) return;
        for (Player p : game.onlinePlayers()) {
            repHero.getWinSound().play(p);
        }
    }

    private @Nullable UUID pickWinRepresentative(Team winningTeam) {
        List<UUID> members = List.copyOf(winningTeam.playerUUIDs());
        if (members.isEmpty()) return null;

        List<UUID> alive = members.stream()
                .filter(game.aliveIds()::contains)
                .toList();
        if (alive.size() == 1) return alive.get(0);
        List<UUID> pool = alive.isEmpty() ? members : alive;

        int bestKills = pool.stream().mapToInt(uuid -> game.getGamePlayer(uuid).kills()).max().orElse(0);
        List<UUID> topKills = pool.stream()
                .filter(uuid -> game.getGamePlayer(uuid).kills() == bestKills)
                .toList();
        if (topKills.size() == 1) return topKills.get(0);

        int bestLives = topKills.stream().mapToInt(uuid -> game.getGamePlayer(uuid).currentLives()).max().orElse(0);
        List<UUID> topLives = topKills.stream()
                .filter(uuid -> game.getGamePlayer(uuid).currentLives() == bestLives)
                .toList();
        return topLives.get(ThreadLocalRandom.current().nextInt(topLives.size()));
    }

    Map<String, String> buildEndPlaceholders(Team winningTeam) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("team", winningTeam.color().displayName());

        String teamPlayers = winningTeam.playerUUIDs().stream()
                .map(uuid -> participantName(game.getGamePlayer(uuid)))
                .collect(Collectors.joining(", "));
        placeholders.put("team_players", teamPlayers);

        List<GamePlayer> topKillers = game.getGamePlayers().values().stream()
                .filter(gp -> gp.kills() > 0)
                .sorted(Comparator.comparingInt(GamePlayer::kills).reversed())
                .limit(TOP_KILLER_COUNT)
                .toList();

        for (int i = 0; i < TOP_KILLER_COUNT; i++) {
            String idx = String.valueOf(i + 1);
            if (i < topKillers.size()) {
                GamePlayer gp = topKillers.get(i);
                placeholders.put("top" + idx + "_name", participantName(gp));
                placeholders.put("top" + idx + "_kills", String.valueOf(gp.kills()));
            } else {
                placeholders.put("top" + idx + "_name", "-");
                placeholders.put("top" + idx + "_kills", "0");
            }
        }
        return placeholders;
    }

    /** Sends the identical end-of-game summary block to everyone who did not win. */
    void sendSummaryToLosers(Team winningTeam, Map<String, String> placeholders) {
        List<Component> lines = GameConfigManager.getMessageList("end.victory.chat", placeholders);
        for (Player p : game.onlinePlayers()) {
            TeamColor color = game.originalColor(p.getUniqueId());
            if (color == null) color = game.playerColor(p.getUniqueId());
            if (game.teamMap().get(color) == winningTeam) continue;
            for (Component line : lines) p.sendMessage(line);
        }
    }

    String participantName(GamePlayer gamePlayer) {
        Player p = Bukkit.getPlayer(gamePlayer.uuid());
        if (p != null) return p.getName();
        if (gamePlayer.bot() != null) return gamePlayer.bot().getName();
        String offline = Bukkit.getOfflinePlayer(gamePlayer.uuid()).getName();
        return offline != null ? offline : "Unknown";
    }

    @Nullable String liveEntityName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        Entity e = Bukkit.getEntity(uuid);
        return e != null ? e.getName() : null;
    }

    private NamedTextColor feedColor(UUID uuid) {
        GamePlayer gamePlayer = game.getGamePlayer(uuid);
        return gamePlayer != null && gamePlayer.team() != null
                ? gamePlayer.team().textColor() : NamedTextColor.RED;
    }
}
