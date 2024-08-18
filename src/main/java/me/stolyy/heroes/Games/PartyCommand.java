package me.stolyy.heroes.Games;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PartyCommand extends Command {
    private final PartyManager partyManager;

    public PartyCommand(PartyManager partyManager) {
        super("party");
        this.partyManager = partyManager;
        this.setDescription("Manage parties");
        this.setUsage("/party <invite/accept/list/leave/disband/transfer> [player]");
        this.setAliases(Arrays.asList("p"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(Component.text("Usage: /party <invite/accept> [player]").color(NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invite":
                if (args.length < 2) {
                    player.sendMessage(Component.text("Usage: /party invite <player1> [player2] [player3] ...").color(NamedTextColor.RED));
                    return true;
                }
                for (int i = 1; i < args.length; i++) {
                    handleInvite(player, args[i]);
                }
                break;
            case "accept":
                handleAccept(player);
                break;
            case "list":
                handleList(player);
                break;
            case "leave":
                handleLeave(player);
                break;
            case "disband":
                handleDisband(player);
                break;
            case "transfer":
                if (args.length != 2) {
                    player.sendMessage(Component.text("Usage: /party transfer <player>").color(NamedTextColor.RED));
                    return true;
                }
                handleTransfer(player, args[1]);
                break;
            default:
                player.sendMessage(Component.text("Unknown subcommand. Use 'invite' or 'accept'.").color(NamedTextColor.RED));
        }

        return true;
    }

    private void handleInvite(Player inviter, String invitedName) {
        Player invited = Bukkit.getPlayer(invitedName);
        if (invited == null) {
            inviter.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        if (partyManager.invitePlayer(inviter, invited)) {
            inviter.sendMessage(Component.text("Invite sent to " + invited.getName() + "!").color(NamedTextColor.GREEN));

            Component inviteMessage = Component.text("You've been invited to join " + inviter.getName() + "'s party! ")
                    .color(NamedTextColor.YELLOW)
                    .append(Component.text("[Accept]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/party accept")));

            invited.sendMessage(inviteMessage);
        } else {
            inviter.sendMessage(Component.text("Unable to send invite. You or the invited player might already be in a party.").color(NamedTextColor.RED));
        }
    }

    private void handleAccept(Player player) {
        Set<UUID> partyMembers = partyManager.acceptInvite(player);
        if (partyMembers != null) {
            player.sendMessage(Component.text("You've joined the party!").color(NamedTextColor.GREEN));
            for (UUID memberUUID : partyMembers) {
                Player member = Bukkit.getPlayer(memberUUID);
                if (member != null && !member.equals(player)) {
                    member.sendMessage(Component.text(player.getName() + " has joined the party!").color(NamedTextColor.GREEN));
                }
            }
        } else {
            player.sendMessage(Component.text("No pending invite found or you're already in a party.").color(NamedTextColor.RED));
        }
    }

    private void handleList(Player player) {
        Set<UUID> partyMembers = partyManager.getPartyMembers(player.getUniqueId());
        if (partyMembers == null) {
            player.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
            return;
        }

        Component message = Component.text("Party members:").color(NamedTextColor.YELLOW);
        for (UUID memberUUID : partyMembers) {
            Player member = Bukkit.getPlayer(memberUUID);
            if (member != null) {
                message = message.append(Component.newline())
                        .append(Component.text("- " + member.getName()).color(NamedTextColor.WHITE));
            }
        }
        player.sendMessage(message);
    }

    private void handleLeave(Player player) {
        if (partyManager.leaveParty(player)) {
            player.sendMessage(Component.text("You have left the party.").color(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("You are not in a party.").color(NamedTextColor.RED));
        }
    }

    private void handleDisband(Player player) {
        if (partyManager.disbandParty(player)) {
            player.sendMessage(Component.text("You have disbanded the party.").color(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("You are not the leader of a party.").color(NamedTextColor.RED));
        }
    }

    private void handleTransfer(Player player, String newLeaderName) {
        Player newLeader = Bukkit.getPlayer(newLeaderName);
        if (newLeader == null) {
            player.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
            return;
        }

        if (partyManager.transferLeadership(player, newLeader)) {
            player.sendMessage(Component.text("You have transferred party leadership to " + newLeader.getName() + ".").color(NamedTextColor.YELLOW));
            newLeader.sendMessage(Component.text("You are now the leader of the party.").color(NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("You are not the leader of a party or the specified player is not in your party.").color(NamedTextColor.RED));
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
        return null;
    }
}