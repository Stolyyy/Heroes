package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Detects lobby returns after ranked games and sends a clickable requeue/auto-queue prompt. */
final class RequeuePrompt {
    // Mode of the game each online player is attached to as a combatant.
    private static final Map<UUID, GameMode> playedMode = new HashMap<>();

    private RequeuePrompt() {}

    static void detectLobbyReturns() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            Game game = GameManager.getPlayerGame(player);
            if (game != null) {
                if (!GameManager.isSpectating(player)) playedMode.put(uuid, game.gameMode());
                continue;
            }
            GameMode mode = playedMode.remove(uuid);
            if (mode != null && mode.hasElo()) send(player, mode);
        }
        playedMode.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    static void send(Player player, GameMode mode) {
        Map<String, String> placeholders = Map.of("mode", mode.displayName());

        if (AutoQueue.isArmed(player)) {
            player.sendMessage(GameConfigManager.getMessage("queue.requeue.auto-active", placeholders)
                    .append(Component.space())
                    .append(GameConfigManager.getMessage("queue.requeue.cancel-button", placeholders)
                            .clickEvent(ClickEvent.runCommand("/autoqueue stop"))
                            .hoverEvent(HoverEvent.showText(
                                    GameConfigManager.getMessage("queue.requeue.cancel-hover", placeholders)))));
            return;
        }

        player.sendMessage(GameConfigManager.getMessage("queue.requeue.prompt", placeholders)
                .append(Component.space())
                .append(GameConfigManager.getMessage("queue.requeue.join-button", placeholders)
                        .clickEvent(ClickEvent.runCommand("/join " + mode.displayName()))
                        .hoverEvent(HoverEvent.showText(
                                GameConfigManager.getMessage("queue.requeue.join-hover", placeholders))))
                .append(Component.space())
                .append(GameConfigManager.getMessage("queue.requeue.auto-button", placeholders)
                        .clickEvent(ClickEvent.runCommand("/autoqueue " + mode.displayName()))
                        .hoverEvent(HoverEvent.showText(
                                GameConfigManager.getMessage("queue.requeue.auto-hover", placeholders)))));
    }

    static void clear() {
        playedMode.clear();
    }
}
