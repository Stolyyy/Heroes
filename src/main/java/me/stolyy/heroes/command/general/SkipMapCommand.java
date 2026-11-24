package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.MapSkipPoll;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

/**
 * {@code /skipmap} - the custom game's leader opens a map-skip poll in the game's chat;
 * {@code /skipmap end} closes it. {@code /skipmap vote <skip|keep> <leader>} is the target of the
 * clickable chat buttons.
 */
public class SkipMapCommand extends HeroesCommand {

    public SkipMapCommand() {
        super("skipmap", "Poll your custom game on skipping the current map", "/skipmap [end]");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("vote")) {
            vote(player, args);
            return;
        }

        Game game = GameManager.getPlayerGame(player);
        if (game == null || !MapSkipPoll.isLeader(player, game)) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.not-leader"));
            return;
        }

        MapSkipPoll existing = MapSkipPoll.of(player);
        if (args.length > 0 && args[0].equalsIgnoreCase("end")) {
            if (existing == null) player.sendMessage(GameConfigManager.getMessage("poll.skipmap.no-poll"));
            else existing.end(false);
            return;
        }

        if (existing != null) {
            player.sendMessage(GameConfigManager.getMessage("poll.skipmap.already-running"));
            return;
        }
        MapSkipPoll.start(player);
    }

    private void vote(Player player, String[] args) {
        if (args.length < 3 || !(args[1].equalsIgnoreCase("skip") || args[1].equalsIgnoreCase("keep"))) {
            player.sendMessage(Component.text("Usage: /skipmap vote <skip|keep> <leader>", NamedTextColor.RED));
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
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabComplete.startsWith(List.of("end"), args[0]);
        return List.of();
    }
}
