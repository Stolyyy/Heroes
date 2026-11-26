package me.stolyy.heroes.command.general;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.command.HeroesCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class VersionCommand extends HeroesCommand {

    public VersionCommand() {
        super("heroesversion", "Shows the running Heroes build",
                "/heroesversion", List.of("hver"), null, false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        sender.sendMessage(Component.text("Heroes " + Heroes.version(), NamedTextColor.GOLD));
    }
}
