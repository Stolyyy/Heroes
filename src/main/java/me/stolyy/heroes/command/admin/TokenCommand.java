package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Admin command: /token give|set|check <player> [amount] — manages unlock-token balances. */
public class TokenCommand extends HeroesCommand {

    public TokenCommand() {
        super("token", "Admin command to manage unlock token balances",
                "/token <give|set|check> <player> [amount]",
                List.of("tokens"), "heroes.token", false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        PlayerStats stats = PlayerStatsManager.getStats(target);

        switch (sub) {
            case "check" -> sender.sendMessage(Component.text(
                    target.getName() + " has " + stats.getTokenBalance() + " token(s).", NamedTextColor.AQUA));
            case "give", "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
                    return;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid integer amount: " + args[2], NamedTextColor.RED));
                    return;
                }
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
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.startsWith(List.of("give", "set", "check"), args[0]);
        }
        if (args.length == 2) {
            return TabComplete.onlinePlayers(args[1]);
        }
        return List.of();
    }
}
