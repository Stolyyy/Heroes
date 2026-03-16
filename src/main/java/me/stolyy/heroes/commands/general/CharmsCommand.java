package me.stolyy.heroes.commands;

import me.stolyy.heroes.gui.BugCharmsGUI;
import me.stolyy.heroes.gui.GUIManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CharmsCommand extends Command {
    public CharmsCommand() {
        super("charms");
        this.setDescription("Opens the charms menu for the Bug character");
        this.setUsage("/charms");
        this.setAliases(List.of("charm", "bugcharms", "bugcharm"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        GUIManager.open(player, new BugCharmsGUI(player));

        return true;
    }
}
