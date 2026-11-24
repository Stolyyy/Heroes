package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.queue.QueueManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class LobbyCommand extends HeroesCommand {
    public LobbyCommand() {
        super("lobby", "Leave the current game or queue",
                "/lobby [1v1|2v2]", List.of("l", "hub", "gg", "leave"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length >= 1) {
            Optional<GameMode> mode = GameMode.fromToken(args[0]).filter(GameMode::hasElo);
            if (mode.isEmpty()) {
                player.sendMessage("Invalid mode. Use 1v1 or 2v2.");
                return;
            }
            QueueManager.leave(player, mode.get());
            return;
        }

        if (QueueManager.isQueued(player)) {
            QueueManager.leaveQueue(player);
            return;
        }

        GameManager.leaveToLobby(player);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.startsWith(GameMode.rankedTokens(), args[0]);
        }
        return Collections.emptyList();
    }
}
