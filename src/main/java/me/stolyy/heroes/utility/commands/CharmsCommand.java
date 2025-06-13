package me.stolyy.heroes.utility.commands;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.menus.BugCharmsGUI;
import me.stolyy.heroes.game.minigame.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class CharmsCommand extends Command {
    public CharmsCommand() {
        super("charms");
        this.setDescription("Opens the charms menu for the Bug character");
        this.setUsage("/charms");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        new BugCharmsGUI(player);

        return true;
    }
}
