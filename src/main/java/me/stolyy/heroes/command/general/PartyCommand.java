package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.party.PartyReadyCheck;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

public class PartyCommand extends HeroesCommand {
    public PartyCommand() {
        super("party", "Manage parties",
                "/party <invite|invite-all|accept|list|leave|disband|transfer|kick|ready> [player|mode...]",
                List.of("p"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            usage(player);
            return;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        Player target = (args.length > 1) ? Bukkit.getPlayer(args[1]) : null;

        switch (subCommand) {
            case "invite" -> {
                if (target == null) {
                    player.sendMessage(Component.text("Player not found or not specified.", NamedTextColor.RED));
                    return;
                }
                PartyManager.invitePlayer(player, target);
            }
            case "invite-all", "inviteall" -> PartyManager.inviteAll(player);
            case "accept" -> {
                if (target == null) {
                    player.sendMessage(Component.text("You must specify whose invite to accept.", NamedTextColor.RED));
                    return;
                }
                PartyManager.acceptInvite(player, target);
            }
            case "list" -> list(player);
            case "leave" -> PartyManager.leaveParty(player);
            case "disband" -> PartyManager.disbandParty(player);
            case "transfer" -> {
                if (target == null) {
                    player.sendMessage(Component.text("You must specify a player to transfer leadership to.", NamedTextColor.RED));
                    return;
                }
                PartyManager.transferLeader(player, target);
            }
            case "kick" -> {
                if (target == null) {
                    player.sendMessage(Component.text("You must specify a player to kick.", NamedTextColor.RED));
                    return;
                }
                PartyManager.kickPlayer(player, target);
            }
            case "ready" -> ready(player, args);
            case "readyvote" -> {
                if (args.length < 2 || !(args[1].equalsIgnoreCase("yes") || args[1].equalsIgnoreCase("no"))) {
                    usage(player);
                    return;
                }
                PartyReadyCheck.vote(player, args[1].equalsIgnoreCase("yes"));
            }
            default -> usage(player);
        }
    }

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
    protected List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();

        if (args.length == 1) {
            return TabComplete.startsWith(
                    List.of("invite", "invite-all", "accept", "list", "leave", "disband", "transfer", "kick", "ready"),
                    args[0]);
        }

        if (args[0].equalsIgnoreCase("ready")) {
            if (args.length == 2) {
                Stream<String> readyTokens = Stream.concat(
                        Stream.concat(Stream.of("force", "cancel"), GameMode.tokens().stream()),
                        GameModePreset.commandTokens().stream());
                return TabComplete.startsWith(readyTokens, args[1]);
            }
            if (args.length == 3) {
                return TabComplete.startsWith(GameMapManager.getAllMapNames(), args[2]);
            }
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "invite" -> {
                    Set<Player> partyMembers = PartyManager.getPartyMembers(player);
                    yield TabComplete.onlinePlayers(args[1], online -> !partyMembers.contains(online));
                }
                case "transfer", "kick" -> {
                    if (!PartyManager.isPartyLeader(player)) yield Collections.emptyList();
                    yield TabComplete.startsWith(
                            PartyManager.getPartyMembers(player).stream()
                                    .filter(member -> !member.equals(player))
                                    .map(Player::getName),
                            args[1]);
                }
                case "accept" -> TabComplete.startsWith(
                        PartyManager.getPendingInvites(player).stream().map(Player::getName),
                        args[1]);
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}
