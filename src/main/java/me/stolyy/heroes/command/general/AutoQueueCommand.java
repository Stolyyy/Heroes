package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.queue.AutoQueue;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AutoQueueCommand extends Command {
    public AutoQueueCommand() {
        super("autoqueue");
        this.setDescription("Automatically rejoin a queue after every game");
        this.setUsage("/autoqueue <1v1|2v2|stop>");
        this.setAliases(Arrays.asList("aq", "autoq"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            GameMode armed = AutoQueue.armedMode(player);
            if (armed == null) {
                player.sendMessage(GameConfigManager.getMessage("queue.auto.not-active"));
                player.sendMessage(getUsage());
            } else {
                player.sendMessage(GameConfigManager.getMessage("queue.auto.status",
                        Map.of("mode", armed.displayName())));
            }
            return true;
        }

        String arg = args[0].toLowerCase();
        if (arg.equals("stop") || arg.equals("off") || arg.equals("cancel")) {
            if (!AutoQueue.stop(player)) {
                player.sendMessage(GameConfigManager.getMessage("queue.auto.not-active"));
            }
            return true;
        }

        GameMode mode = parseGameMode(arg);
        if (mode == null) {
            player.sendMessage(getUsage());
            return true;
        }

        AutoQueue.arm(player, mode);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("1v1", "2v2", "stop").stream()
                    .filter(s -> s.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }

    private GameMode parseGameMode(String input) {
        return switch (input) {
            case "1v1" -> GameMode.ONE_V_ONE;
            case "2v2" -> GameMode.TWO_V_TWO;
            default -> null;
        };
    }
}
