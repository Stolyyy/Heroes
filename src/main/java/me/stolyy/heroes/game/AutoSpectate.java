package me.stolyy.heroes.game;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent auto-spectate: {@code /autospec <player>} arms a watcher on a target, and every time
 * the target's game starts, the watcher is pulled in as a spectator — as long as the watcher
 * isn't PLAYING a game themselves at that moment. Watching from another game's spectator seat
 * doesn't block it (the fire re-targets them, which is the point of following someone).
 *
 * <p>The arm survives fires (persistent by design). It clears when the watcher runs
 * {@code /autospec off}, joins a game as a participant ({@link #onJoinedGame}), or logs out.
 */
public final class AutoSpectate {

    // watcher uuid -> target uuid
    private static final Map<UUID, UUID> targets = new HashMap<>();
    // watcher uuid -> action bar refresher (lobby only; started on arm, cancelled on disarm/quit)
    private static final Map<UUID, BukkitTask> actionBars = new HashMap<>();
    private static boolean initialized;

    private AutoSpectate() {}

    public static void initialize() {
        if (initialized) return;
        Bukkit.getPluginManager().registerEvents(new QuitListener(), Heroes.getInstance());
        initialized = true;
    }

    /** Arms (or re-targets) the watcher; fires immediately if the target is already in an active game. */
    public static void arm(Player watcher, Player target) {
        targets.put(watcher.getUniqueId(), target.getUniqueId());
        startActionBar(watcher);
        watcher.sendMessage(Component.text("Auto-spectate armed on " + target.getName()
                + " — /autospec off to stop.", NamedTextColor.AQUA));

        Game game = GameManager.getPlayerGame(target);
        if (game != null && (game.gameState() == GameState.STARTING
                || game.gameState() == GameState.IN_PROGRESS)) {
            tryFire(watcher, target);
        }
    }

    /** @return true if an arm existed and was cleared */
    public static boolean disarm(UUID watcher) {
        stopActionBar(watcher);
        return targets.remove(watcher) != null;
    }

    /** The armed target's uuid for this watcher, or null. */
    public static UUID target(UUID watcher) {
        return targets.get(watcher);
    }

    /**
     * Game-start hook (fired from {@link Game#start} once every participant is placed): pull in
     * every armed watcher whose target plays in this game.
     */
    public static void onGameStarting(Game game) {
        for (Map.Entry<UUID, UUID> entry : Map.copyOf(targets).entrySet()) {
            Player target = Bukkit.getPlayer(entry.getValue());
            if (target == null || GameManager.getPlayerGame(target) != game) continue;
            Team targetTeam = game.playerTeam(target.getUniqueId());
            if (targetTeam == null || targetTeam.color() == TeamColor.SPECTATOR) continue;

            Player watcher = Bukkit.getPlayer(entry.getKey());
            if (watcher == null) continue;
            tryFire(watcher, target);
        }
    }

    /**
     * A player entered a game as a PARTICIPANT — their own arm is void ("as long as you're not in
     * a game yourself" extends to: queuing up cancels the follow). Called from Game.addPlayer for
     * non-spectator joins.
     */
    public static void onJoinedGame(Player player) {
        stopActionBar(player.getUniqueId());
        if (targets.remove(player.getUniqueId()) != null) {
            player.sendMessage(Component.text("Auto-spectate cancelled — you joined a game.", NamedTextColor.GRAY));
        }
    }

    private static void startActionBar(Player watcher) {
        stopActionBar(watcher.getUniqueId());
        UUID watcherId = watcher.getUniqueId();
        actionBars.put(watcherId, new BukkitRunnable() {
            @Override
            public void run() {
                Player online = Bukkit.getPlayer(watcherId);
                if (GameManager.isPlayerInGame(online)) return;
                String name = Bukkit.getOfflinePlayer(targets.get(watcherId)).getName();
                online.sendActionBar(GameConfigManager.getMessage("autospectate.actionbar", Map.of("player", name)));
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L));
    }

    private static void stopActionBar(UUID watcher) {
        BukkitTask task = actionBars.remove(watcher);
        if (task != null) task.cancel();
    }

    private static void tryFire(Player watcher, Player target) {
        // Playing blocks the fire (arm stays for the target's next game); spectating elsewhere
        // doesn't — the fire simply re-seats the watcher.
        if (GameManager.isPlayerInGame(watcher) && !GameManager.isSpectating(watcher)) return;
        GameManager.spectateGame(watcher, target);
    }

    public static void clear() {
        for (BukkitTask task : actionBars.values()) {
            task.cancel();
        }
        actionBars.clear();
        targets.clear();
    }

    /** Watcher arms die with the connection. */
    public static class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            targets.remove(event.getPlayer().getUniqueId());
            stopActionBar(event.getPlayer().getUniqueId());
        }
    }
}
