package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Admin command: /token give|set|check <player> [amount] — manages unlock-token balances. */
public class TokenCommand extends Command {

    public TokenCommand() {
        super("token");
        this.setDescription("Admin command to manage unlock token balances");
        this.setUsage("/token <give|set|check> <player> [amount]");
        this.setPermission("heroes.token");
        this.setAliases(List.of("tokens"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return true;
        }

        String sub = args[0].toLowerCase();
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return true;
        }
        PlayerStats stats = PlayerStatsManager.getStats(target);

        switch (sub) {
            case "check" -> sender.sendMessage(Component.text(
                    target.getName() + " has " + stats.getTokenBalance() + " token(s).", NamedTextColor.AQUA));
            case "give", "set" -> {
                if (args.length < 3 || !args[2].matches("-?\\d+")) {
                    sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
                    return true;
                }
                int amount = Integer.parseInt(args[2]);
                if (sub.equals("give")) stats.addTokens(amount);
                else stats.setTokenBalance(amount);

                sender.sendMessage(Component.text(target.getName() + " now has "
                        + stats.getTokenBalance() + " token(s).", NamedTextColor.GREEN));
                if (target != sender) {
                    target.sendMessage(Component.text("Your token balance is now "
                            + stats.getTokenBalance() + ".", NamedTextColor.GOLD));
                }
                LobbyScoreboard.apply(target);
            }
            default -> sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("give", "set", "check").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
