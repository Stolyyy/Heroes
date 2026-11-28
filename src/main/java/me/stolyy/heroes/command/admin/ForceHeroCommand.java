package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Admin command: /forcehero <player> <hero> — selects any hero for a player, bypassing unlock and admin-only gates. */
public class ForceHeroCommand extends HeroesCommand {

    public ForceHeroCommand() {
        super("forcehero", "Admin command to forcibly select a hero for a player",
                "/forcehero <player> <hero>", List.of("forceselect"), "heroes.forcehero", false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + args[0], NamedTextColor.RED));
            return;
        }

        String heroName = HeroConfig.resolve(args[1]);
        if (!HeroConfig.exists(heroName) || heroName.equalsIgnoreCase("default")) {
            sender.sendMessage(Component.text("Hero not found: " + args[1], NamedTextColor.RED));
            return;
        }

        HeroManager.forceSelectHero(target, heroName);
        sender.sendMessage(Component.text("Forced " + target.getName() + "'s hero to "
                + HeroConfig.formatName(heroName)
                + (GameManager.isPlayerInGame(target) ? " (takes effect next game)." : "."),
                NamedTextColor.GREEN));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.onlinePlayers(args[0]);
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>(HeroConfig.getAvailableNames());
            options.addAll(HeroConfig.aliases().keySet());
            return TabComplete.startsWith(options, args[1]);
        }
        return List.of();
    }
}
