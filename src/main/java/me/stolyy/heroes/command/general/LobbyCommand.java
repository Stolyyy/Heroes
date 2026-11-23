package me.stolyy.heroes.command.general;

import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.queue.AutoQueue;
import me.stolyy.heroes.game.queue.QueueManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LobbyCommand extends Command {
    public LobbyCommand() {
        super("lobby");
        this.setDescription("Leave the current game or queue");
        this.setUsage("/lobby [1v1|2v2]");
        this.setAliases(Arrays.asList("l", "hub", "gg", "leave"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length >= 1) {
            // /leave 1v1 or /leave 2v2 — leave specific queue
            GameMode mode = parseGameMode(args[0]);
            if (mode == null) {
                player.sendMessage("Invalid mode. Use 1v1 or 2v2.");
                return true;
            }
            if (QueueManager.isQueuedFor(player, mode)) {
                // leaveQueue(mode) also disarms a matching auto-queue.
                QueueManager.leaveQueue(player, mode);
            } else if (AutoQueue.armedMode(player) == mode) {
                // Not in that queue right now (usually mid-match) but auto-queueing it — the
                // mode-argument form is THE gesture for stepping out of the loop while in a
                // game, where a plain /leave deliberately keeps the loop armed.
                AutoQueue.stop(player);
            } else {
                player.sendMessage(GameConfigManager.getMessage("queue.not-in-queue",
                    Map.of("mode", mode.displayName())));
            }
            return true;
        }

        // No args — leave all queues, or leave game
        if (QueueManager.isQueued(player)) {
            QueueManager.leaveQueue(player);
            return true;
        }

        GameManager.leaveGame(player, true);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("1v1", "2v2").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        return Collections.emptyList();
    }

    private GameMode parseGameMode(String input) {
        return switch (input.toLowerCase()) {
            case "1v1" -> GameMode.ONE_V_ONE;
            case "2v2" -> GameMode.TWO_V_TWO;
            default -> null;
        };
    }
}
