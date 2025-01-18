package me.stolyy.heroes.Game.Commands;

import me.stolyy.heroes.Game.Party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class PartyChat extends Command {

    public PartyChat(PartyManager partyManager) {
        super("partychat");
        this.setDescription("Send a message to your party");
        this.setUsage("/partychat <message>");
        this.setAliases(Arrays.asList("pc"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /partychat <message>").color(NamedTextColor.RED));
            return true;
        }

        Component message = Component.text(String.join(" ", args));
        PartyManager.sendMessage(player, message);
        return true;
    }
}
