package me.stolyy.heroes.command.general;

import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class VersionCommand extends Command {

    public VersionCommand() {
        super("version");
        this.setDescription("Shows the running Heroes build");
        this.setUsage("/version");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        sender.sendMessage(Component.text("Heroes " + Heroes.version(), NamedTextColor.GOLD));
        return true;
    }
}
