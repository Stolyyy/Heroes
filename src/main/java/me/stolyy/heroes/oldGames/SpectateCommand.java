package me.stolyy.heroes.oldGames;

import me.stolyy.heroes.Heroes;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SpectateCommand extends Command {
    private final Heroes plugin;
    private final GameManager gameManager;

    public SpectateCommand(Heroes plugin, GameManager gameManager) {
        super("spectate");
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.setDescription("Spectate a player's game");
        this.setUsage("/spectate <player>");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Player spectator = (Player) sender;

        if (args.length != 1) {
            spectator.sendMessage("Usage: /spectate <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            spectator.sendMessage("Player not found.");
            return true;
        }

        Game game = gameManager.getPlayerGame(target);
        if (game == null) {
            spectator.sendMessage("That player is not in a game.");
            return true;
        }

        if (game.addSpectator(spectator)) {
            spectator.sendMessage("You are now spectating " + target.getName() + "'s game.");
        } else {
            spectator.sendMessage("Unable to spectate the game.");
        }

        return true;
    }
}