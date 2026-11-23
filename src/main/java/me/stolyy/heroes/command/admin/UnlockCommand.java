package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Admin command: /unlock <hero> [player] — forcibly unlocks a hero (target defaults to the sender). */
public class UnlockCommand extends Command {

    public UnlockCommand() {
        super("unlock");
        this.setDescription("Admin command to forcibly unlock a hero for a player");
        this.setUsage("/unlock <hero> [player]");
        this.setPermission("heroes.unlock");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return true;
        }

        String heroName = args[0].toLowerCase();
        if (!HeroConfig.exists(heroName)) {
            sender.sendMessage(Component.text("Hero not found: " + args[0], NamedTextColor.RED));
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
        } else {
            if (!(sender instanceof Player senderPlayer)) {
                sender.sendMessage(Component.text("Specify a player when running from console: " + getUsage(), NamedTextColor.YELLOW));
                return true;
            }
            target = senderPlayer;
        }

        PlayerStats stats = PlayerStatsManager.getStats(target);
        String display = HeroConfig.formatName(heroName);
        if (stats.isHeroUnlocked(heroName) || HeroConfig.isDefaultUnlocked(heroName)) {
            sender.sendMessage(Component.text(display + " is already unlocked for " + target.getName() + ".", NamedTextColor.YELLOW));
            return true;
        }

        stats.unlockHero(heroName);
        sender.sendMessage(Component.text("Unlocked " + display + " for " + target.getName() + ".", NamedTextColor.GREEN));
        if (target != sender) {
            target.sendMessage(Component.text(display + " has been unlocked for you!", NamedTextColor.GOLD));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return HeroConfig.getAvailableNames().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .sorted()
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
