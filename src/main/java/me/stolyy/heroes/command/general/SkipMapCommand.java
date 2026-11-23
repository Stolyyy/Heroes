package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.MapSkipPoll;
import me.stolyy.heroes.util.GameConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * {@code /skipmap} - the custom game's leader opens a map-skip poll in the game's chat;
 * {@code /skipmap end} closes it. {@code /skipmap vote <skip|keep> <leader>} is the target of the
 * clickable chat buttons.
 */
public class SkipMapCommand extends Command {

    public SkipMapCommand() {
        super("skipmap");
        this.setDescription("Poll your custom game on skipping the current map");
        this.setUsage("/skipmap [end]");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("vote")) {
            vote(player, args);
            return true;
        }

        Game game = GameManager.getPlayerGame(player);
        if (game == null || !MapSkipPoll.isLeader(player, game)) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.not-leader"));
            return true;
        }

        MapSkipPoll existing = MapSkipPoll.of(player);
        if (args.length > 0 && args[0].equalsIgnoreCase("end")) {
            if (existing == null) player.sendMessage(GameConfigManager.getMessage("poll.skipmap.no-poll"));
            else existing.end(false);
            return true;
        }

        if (existing != null) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.already-running"));
            return true;
        }
        MapSkipPoll.start(player);
        return true;
    }

    private void vote(Player player, String[] args) {
        if (args.length < 3 || !(args[1].equalsIgnoreCase("skip") || args[1].equalsIgnoreCase("keep"))) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.no-poll"));
            return;
        }
        MapSkipPoll poll = MapSkipPoll.byLeaderName(args[2]);
        if (poll == null) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.no-poll"));
            return;
        }
        if (!poll.vote(player, args[1].equalsIgnoreCase("skip"))) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.not-participant", Map.of("leader", args[2])));
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && "end".startsWith(args[0].toLowerCase())) return List.of("end");
        return List.of();
    }
}
