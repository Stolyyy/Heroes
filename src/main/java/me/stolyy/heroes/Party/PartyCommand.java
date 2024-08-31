package me.stolyy.heroes.Party;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class PartyCommand extends Command {
    private final PartyManager partyManager;

    public PartyCommand(PartyManager partyManager) {
        super("party");
        this.partyManager = partyManager;
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
            sendUsage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invite":
                handleInvite(player, args);
                break;
            case "accept":
                partyManager.acceptInvite(player);
                break;
            case "list":
                handleList(player);
                break;
            case "leave":
                partyManager.leaveParty(player);
                break;
            case "disband":
                partyManager.disbandParty(player);
                break;
            case "transfer":
                handleTransfer(player, args);
                break;
            default:
                sendUsage(player);
        }

        return true;
    }

    private void handleInvite(Player inviter, String[] args) {
        if (args.length < 2) {
            inviter.sendMessage(Component.text("Usage: /party invite <player>").color(NamedTextColor.RED));
            return;
        }

        Player invited = Bukkit.getPlayer(args[1]);
        if (invited == null) {
            inviter.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        partyManager.invitePlayer(inviter, invited);
    }

    private void handleList(Player player) {
        Set<UUID> members = partyManager.getPartyMembers(player.getUniqueId());
        if (members == null || members.isEmpty()) {
            player.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }

        int memberCount = members.size();
        Component message = Component.text("Party members (" + memberCount + "):").color(NamedTextColor.YELLOW);
        for (UUID memberUUID : members) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                message = message.append(Component.newline())
                        .append(Component.text("- " + member.getName()).color(NamedTextColor.WHITE));
            }
        }
        player.sendMessage(message);
    }

    private void handleTransfer(Player currentLeader, String[] args) {
        if (args.length < 2) {
            currentLeader.sendMessage(Component.text("Usage: /party transfer <player>").color(NamedTextColor.RED));
            return;
        }

        Player newLeader = Bukkit.getPlayer(args[1]);
        if (newLeader == null) {
            currentLeader.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        partyManager.transferLeadership(currentLeader, newLeader);
    }

    private void sendUsage(Player player) {
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