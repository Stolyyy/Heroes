package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.ui.gui.BugCharmsGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

public class CharmsCommand extends HeroesCommand {
    public CharmsCommand() {
        super("charms", "Opens the charms menu for the Bug character",
                "/charms", List.of("charm", "bugcharms", "bugcharm"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (HeroManager.getHeroName(player) == null) {
            player.sendMessage(Component.text("You don't have a hero selected.", NamedTextColor.RED));
            return;
        }

        GUIManager.open(player, new BugCharmsGUI(player));
    }
}
