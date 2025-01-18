package me.stolyy.heroes.Game.Commands;

import me.stolyy.heroes.Game.Party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class PartyC extends Command {
    public PartyC() {
        super("party");
        this.setDescription("Manage parties");
        this.setUsage("/party <invite|accept|list|leave|disband|transfer> [player]");
        this.setAliases(Arrays.asList("p"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            usage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invite":
                invite(player, args);
                break;
            case "accept":
                PartyManager.acceptInvite(args[1], player);
                break;
            case "list":
                list(player);
                break;
            case "leave":
                PartyManager.leaveParty(player);
                break;
            case "disband":
                PartyManager.disbandParty(player);
                break;
            case "transfer":
                transfer(player, args);
                break;
            default:
                usage(player);
        }

        return true;
    }

    private void invite(Player inviter, String[] args) {
        if (args.length < 2) {
            inviter.sendMessage(Component.text("Usage: /party invite <player>").color(NamedTextColor.RED));
            return;
        }

        Player invited = Bukkit.getPlayer(args[1]);
        if (invited == null) {
            inviter.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        PartyManager.invitePlayer(inviter, invited);
    }

    private void list(Player player) {
        Set<Player> members = PartyManager.getPlayersInParty(player);
        if (members == null || members.isEmpty()) {
            player.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }
        int memberCount = members.size();
        Component message = Component.text("PartyC members (" + memberCount + "):").color(NamedTextColor.YELLOW);
        for (Player p : members) {
            if (p != null) {
                message = message.append(Component.newline())
                        .append(Component.text("- " + p.getName()).color(NamedTextColor.WHITE));
            }
        }
        player.sendMessage(message);
    }

    private void transfer(Player currentLeader, String[] args) {
        if (args.length < 2) {
            currentLeader.sendMessage(Component.text("Usage: /party transfer <player>").color(NamedTextColor.RED));
            return;
        }

        Player newLeader = Bukkit.getPlayer(args[1]);
        if (newLeader == null) {
            currentLeader.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        PartyManager.transferLeader(currentLeader, newLeader);
    }

    private void usage(Player player) {
        player.sendMessage(Component.text("Usage: /party <invite|accept|list|leave|disband|transfer> [player]").color(NamedTextColor.RED));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            return Arrays.asList("invite", "accept", "list", "leave", "disband", "transfer");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("transfer"))) {
            return null; // Return null to default to online player names
        }
        return Collections.emptyList();
    }
}
