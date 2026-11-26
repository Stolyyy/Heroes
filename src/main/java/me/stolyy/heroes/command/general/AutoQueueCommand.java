package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.queue.AutoQueue;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AutoQueueCommand extends HeroesCommand {
    public AutoQueueCommand() {
        super("autoqueue", "Automatically rejoin a queue after every game",
                "/autoqueue <1v1|2v2|stop>", List.of("aq", "autoq"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            GameMode armed = AutoQueue.armedMode(player);
            if (armed == null) {
                player.sendMessage(GameConfigManager.getMessage("queue.auto.not-active"));
                player.sendMessage(getUsage());
            } else {
                player.sendMessage(GameConfigManager.getMessage("queue.auto.status",
                        Map.of("mode", armed.displayName())));
            }
            return;
        }

        String arg = args[0].toLowerCase(Locale.ROOT);
        if (arg.equals("stop") || arg.equals("off") || arg.equals("cancel")) {
            if (!AutoQueue.stop(player)) {
                player.sendMessage(GameConfigManager.getMessage("queue.auto.not-active"));
            }
            return;
        }

        GameMode mode = GameMode.fromToken(arg).filter(GameMode::hasElo).orElse(null);
        if (mode == null) {
            player.sendMessage(getUsage());
            return;
        }

        AutoQueue.arm(player, mode);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.startsWith(List.of("1v1", "2v2", "stop"), args[0]);
        }
        return Collections.emptyList();
    }
}
