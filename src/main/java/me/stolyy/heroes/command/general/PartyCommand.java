package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.party.PartyReadyCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PartyCommand extends Command {
    public PartyCommand() {
        super("party");
        this.setDescription("Manage parties");
        this.setUsage("/party <invite|invite-all|accept|list|leave|disband|transfer|kick|ready> [player|mode...]");
        this.setAliases(List.of("p"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            usage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : null;

        switch (subCommand) {
            case "invite":
                if (target == null) {
                    player.sendMessage(Component.text("Player not found or not specified.", NamedTextColor.RED));
                    return true;
                }
                PartyManager.invitePlayer(player, target);
                break;
            case "invite-all":
            case "inviteall":
                PartyManager.inviteAll(player);
                break;
            case "accept":
                if (target == null) {
                    player.sendMessage(Component.text("You must specify whose invite to accept.", NamedTextColor.RED));
                    return true;
                }
                PartyManager.acceptInvite(player, target);
                QueueManager.removeFromAllQueues(player);
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
                if (target == null) {
                    player.sendMessage(Component.text("You must specify a player to transfer leadership to.", NamedTextColor.RED));
                    return true;
                }
                PartyManager.transferLeader(player, target);
                break;
            case "kick":
                if (target == null) {
                    player.sendMessage(Component.text("You must specify a player to kick.", NamedTextColor.RED));
                    return true;
                }
                PartyManager.kickPlayer(player, target);
                break;
            case "ready":
                ready(player, args);
                break;
            case "readyvote":
                if (args.length < 2 || !(args[1].equalsIgnoreCase("yes") || args[1].equalsIgnoreCase("no"))) {
                    usage(player);
                    return true;
                }
                PartyReadyCheck.vote(player, args[1].equalsIgnoreCase("yes"));
                break;
            default:
                usage(player);
        }
        return true;
    }

    /** {@code /party ready <mode> [args...]} (same grammar as /join), {@code force} or {@code cancel}. */
    private void ready(Player player, String[] args) {
        if (args.length < 2) {
            usage(player);
            return;
        }
        if (args[1].equalsIgnoreCase("force")) {
            PartyReadyCheck.force(player);
            return;
        }
        if (args[1].equalsIgnoreCase("cancel")) {
            PartyReadyCheck.cancel(player);
            return;
        }

        String[] joinArgs = Arrays.copyOfRange(args, 1, args.length);
        JoinCommand.JoinRequest request = JoinCommand.parse(player, joinArgs);
        if (request == null) return;
        PartyReadyCheck.start(player, String.join(" ", joinArgs), leader -> JoinCommand.perform(leader, request));
    }

    private void list(Player player) {
        Set<Player> members = PartyManager.getPartyMembers(player);
        if (members.isEmpty()) {
            player.sendMessage(Component.text("You are not in a party.", NamedTextColor.RED));
            return;
        }

        Component message = Component.text("Party Members (" + members.size() + "):", NamedTextColor.YELLOW);
        for (Player p : members) {
            message = message.append(Component.newline())
                    .append(Component.text("- " + p.getName(), NamedTextColor.WHITE));
        }
        player.sendMessage(message);
    }

    private void usage(Player player) {
        player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.RED));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) throws IllegalArgumentException {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            return Arrays.asList("invite", "invite-all", "accept", "list", "leave", "disband", "transfer", "kick", "ready");
        }

        if (args[0].equalsIgnoreCase("ready")) {
            if (args.length == 2) {
                return Stream.of("force", "cancel", "1v1", "2v2", "custom", "tdm", "evolution", "series", "nuzlocke", "ironman", "boss")
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                return GameMapManager.getAllMapNames().stream()
                        .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "invite":
                    Set<Player> partyMembers = PartyManager.getPartyMembers(player);
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(onlinePlayer -> !partyMembers.contains(onlinePlayer))
                            .map(Player::getName)
                            .collect(Collectors.toList());
                case "transfer":
                case "kick":
                    if (!PartyManager.isPartyLeader(player)) return Collections.emptyList();
                    return PartyManager.getPartyMembers(player).stream()
                            .filter(member -> !member.equals(player))
                            .map(Player::getName)
                            .collect(Collectors.toList());
                case "accept":
                    return PartyManager.getPendingInvites(player).stream()
                            .map(Player::getName)
                            .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
