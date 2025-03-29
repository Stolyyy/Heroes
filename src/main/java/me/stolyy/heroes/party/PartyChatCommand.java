package me.stolyy.heroes.party;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PartyChatCommand extends Command {

    public PartyChatCommand() {
        super("partychat");
        this.setDescription("Send a message to your party");
        this.setUsage("/partychat <message>");
        this.setAliases(List.of("pc"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /partychat <message>").color(NamedTextColor.RED));
            return true;
        }

        Component message = Component.text(String.join(" ", args));
        PartyManager.sendMessage(player, message);
        return true;
    }
}
