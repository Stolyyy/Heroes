package me.stolyy.heroes.command.general;

import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.ui.gui.BugCharmsGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

        String heroName = HeroManager.getHeroName(player);
        if (heroName == null || heroName.equalsIgnoreCase("none")) {
            player.sendMessage(Component.text("You don't have a hero selected.", NamedTextColor.RED));
            return true;
        }

        GUIManager.open(player, new BugCharmsGUI(player));

        return true;
    }
}
