package me.stolyy.heroes.utility.commands;

import me.stolyy.heroes.game.minigame.GameManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SpectateCommand extends Command {
    //adds player as a spectator to the game of a player
    //spectate (player)
    public SpectateCommand() {
        super("spectate");
        this.setDescription("Spectate a player's game");
        this.setUsage("/spectate <player>");
        this.setAliases(List.of("spec"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("Usage: /spectate <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("Player not found.");
            return true;
        }

        GameManager.spectateGame(player, target);
        return true;
    }
}
