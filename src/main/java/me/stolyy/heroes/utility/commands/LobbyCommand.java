package me.stolyy.heroes.utility.commands;

import me.stolyy.heroes.game.minigame.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class LobbyCommand extends Command {
    //makes player leave game if in one
    public LobbyCommand() {
        super("lobby");
        this.setDescription("Leave the current game");
        this.setUsage("/lobby");
        this.setAliases(Arrays.asList("l", "hub", "gg", "leave"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        GameManager.leaveGame(player, true);

        return true;
    }
}

