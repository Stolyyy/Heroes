package me.stolyy.heroes.Games;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.Games.Game;
import me.stolyy.heroes.Games.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LeaveCommand extends Command {
    private final Heroes plugin;
    private final GameManager gameManager;

    public LeaveCommand(Heroes plugin, GameManager gameManager) {
        super("leave");
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.setDescription("Leave the current game");
        this.setUsage("/leave");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;
        Game game = gameManager.getPlayerGame(player);

        if (game == null) {
            player.sendMessage("You are not in a game.");
            return true;
        }

        gameManager.leaveGame(player);
        plugin.teleportToLobby(player);
        player.sendMessage("You have left the game and been teleported to spawn.");

        return true;
    }
}