package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/** Admin command: /unlock <hero> [player] — forcibly unlocks a hero (target defaults to the sender). */
public class UnlockCommand extends HeroesCommand {

    public UnlockCommand() {
        super("unlock", "Admin command to forcibly unlock a hero for a player",
                "/unlock <hero> [player]", List.of("unlockhero"), "heroes.unlock", false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        String heroName = args[0].toLowerCase(Locale.ROOT);
        if (!HeroConfig.exists(heroName)) {
            sender.sendMessage(Component.text("Hero not found: " + args[0], NamedTextColor.RED));
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return;
            }
        } else {
            if (!(sender instanceof Player senderPlayer)) {
                sender.sendMessage(Component.text("Specify a player when running from console: " + getUsage(), NamedTextColor.YELLOW));
                return;
            }
            target = senderPlayer;
        }

        PlayerStats stats = PlayerStatsManager.getStats(target);
        String display = HeroConfig.formatName(heroName);
        if (stats.isHeroUnlocked(heroName) || HeroConfig.isDefaultUnlocked(heroName)) {
            sender.sendMessage(Component.text(display + " is already unlocked for " + target.getName() + ".", NamedTextColor.YELLOW));
            return;
        }

        stats.unlockHero(heroName);
        sender.sendMessage(Component.text("Unlocked " + display + " for " + target.getName() + ".", NamedTextColor.GREEN));
        if (target != sender) {
            target.sendMessage(Component.text(display + " has been unlocked for you!", NamedTextColor.GOLD));
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.startsWith(HeroConfig.getAvailableNames(), args[0]);
        }
        if (args.length == 2) {
            return TabComplete.onlinePlayers(args[1]);
        }
        return List.of();
    }
}
