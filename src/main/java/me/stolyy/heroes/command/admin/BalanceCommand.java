package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.TemplateConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BalanceCommand extends HeroesCommand {

    public BalanceCommand() {
        super("balance", "Admin command for balancing hero stats",
                "/balance <hero|default|list|reload> [path...] [value]",
                List.of("bal", "herobalance"), "heroes.balance", true);
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  /balance list - List all heroes", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance reload - Reload all configs", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero|default> - Show top-level keys", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero|default> <path> - Get value", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero|default> <path> <value> - Set value", NamedTextColor.GRAY));
            return;
        }

        if (args[0].equalsIgnoreCase("list")) {
            List<String> heroes = HeroConfig.getAvailableNames();
            player.sendMessage(Component.text("Available heroes: " + String.join(", ", heroes), NamedTextColor.AQUA));
            return;
        }

        String heroName = args[0].toLowerCase(Locale.ROOT);
        if (heroName.equals("reload")) {
            TemplateConfig.reload();
            HeroConfig.load();
            player.sendMessage(Component.text("Hero and template configurations reloaded.", NamedTextColor.GREEN));
            return;
        }

        if (!heroName.equals("default") && !HeroConfig.exists(heroName)) {
            player.sendMessage(Component.text("Hero not found: " + heroName, NamedTextColor.RED));
            return;
        }

        HeroConfig.handleConfigCommand(player, heroName, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(HeroConfig.getAvailableNames());
            options.add("list");
            options.add("reload");
            options.add("default");
            return TabComplete.startsWith(options, args[0]);
        }

        String heroName = args[0].toLowerCase(Locale.ROOT);
        if (heroName.equals("list") || !HeroConfig.exists(heroName)) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return TabComplete.startsWith(HeroConfig.getConfigKeys(heroName, ""), args[1]);
        }

        String path = String.join(".", Arrays.copyOfRange(args, 1, args.length - 1));
        return TabComplete.startsWith(HeroConfig.getConfigKeys(heroName, path), args[args.length - 1]);
    }
}
