package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.GameConfigManager.AnnouncerCue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Kill feed broadcasts, announcer voice lines, win-sound representative selection, and post-game
 * summary messages.
 */
final class MatchAnnouncer {
    private static final int TOP_KILLER_COUNT = 3;

    enum Event {
        COUNTDOWN("countdown", false),
        MATCH_START("match-start", false),
        FIRST_BLOOD("first-blood", false),
        LAST_LIFE("last-life", false),
        VICTORY("victory", true),
        DEFEAT("defeat", true);

        private final String configKey;
        private final boolean postMatch;

        Event(String configKey, boolean postMatch) {
            this.configKey = configKey;
            this.postMatch = postMatch;
        }

        String configKey() { return configKey; }
        boolean postMatch() { return postMatch; }
    }

    private final Game game;
    private final List<BukkitTask> pendingTasks = new ArrayList<>();
    private boolean firstBloodAnnounced;
    private boolean cleaned;

    MatchAnnouncer(Game game) {
        this.game = game;
    }

    // --- Announcer voice lines ---

    void onCountdownStart() {
        announce(Event.COUNTDOWN, game::onlinePlayers);
    }

    void onMatchStart() {
        announce(Event.MATCH_START, game::onlinePlayers);
    }

    void onCreditedKill() {
        if (firstBloodAnnounced) return;
        firstBloodAnnounced = true;
        announce(Event.FIRST_BLOOD, game::onlinePlayers);
    }

    void onLastLife(Player participant) {
        if (!Interactions.isRealPlayer(participant)) return;
        UUID targetId = participant.getUniqueId();
        announce(Event.LAST_LIFE, () -> {
            Player p = Bukkit.getPlayer(targetId);
            return p != null ? List.of(p) : List.of();
        });
    }

    void onMatchEnd(Team winningTeam) {
        Set<UUID> victoryAudience = new HashSet<>();
        Set<UUID> defeatAudience = new HashSet<>();
        for (Player p : game.onlinePlayers()) {
            if (!Interactions.isRealPlayer(p)) continue;
            TeamColor original = game.originalColor(p.getUniqueId());
            if (original == null || original == TeamColor.SPECTATOR || original == winningTeam.color()) {
                victoryAudience.add(p.getUniqueId());
            } else {
                defeatAudience.add(p.getUniqueId());
            }
        }
        announce(Event.VICTORY, () -> resolvePlayers(victoryAudience));
        announce(Event.DEFEAT, () -> resolvePlayers(defeatAudience));
    }

    void cancelTasks() {
        pendingTasks.forEach(BukkitTask::cancel);
        pendingTasks.clear();
    }

    void markCleaned() {
        cleaned = true;
        cancelTasks();
    }

    private void announce(Event event, Supplier<Collection<Player>> audienceSupplier) {
        AnnouncerCue cue = GameConfigManager.getAnnouncerCue(event.configKey());
        if (cue.sound().isEmpty()) return;

        long delayTicks = Math.round(cue.delayMs() / 50.0);
        if (delayTicks <= 0) {
            playToAudience(cue, audienceSupplier.get());
            return;
        }

        BukkitTask[] holder = new BukkitTask[1];
        BukkitTask task = Bukkit.getScheduler().runTaskLater(Heroes.getInstance(), () -> {
            pendingTasks.remove(holder[0]);
            if (cleaned) return;
            if (!event.postMatch() && game.gameState() == GameState.ENDED) return;
            playToAudience(cue, audienceSupplier.get());
        }, delayTicks);
        holder[0] = task;
        pendingTasks.add(task);
    }

    private void playToAudience(AnnouncerCue cue, Collection<Player> audience) {
        for (Player p : audience) {
            if (!p.isOnline() || !Interactions.isRealPlayer(p)) continue;
            if (GameManager.getEntityGame(p.getUniqueId()) != game) continue;
            cue.sound().playPersonal(p);
        }
    }

    private static List<Player> resolvePlayers(Set<UUID> uuids) {
        List<Player> out = new ArrayList<>(uuids.size());
        for (UUID id : uuids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    // --- Kill feed & end-of-game summaries ---

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
