package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.TemplateConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class BalanceCommand extends Command {

    public BalanceCommand() {
        super("balance");
        this.setDescription("Admin command for balancing hero stats");
        this.setUsage("/balance <hero> <path...> [value]");
        this.setPermission("heroes.balance");
        this.setAliases(List.of("bal", "herobalance"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  /balance list - List all heroes", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero> - Show top-level keys", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero> reload - Reload all configs", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero> default - Reset this hero to disk values", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero> <path> - Get value", NamedTextColor.GRAY));
            player.sendMessage(Component.text("  /balance <hero> <path> <value> - Set value", NamedTextColor.GRAY));
            return true;
        }

        // /balance list
        if (args[0].equalsIgnoreCase("list")) {
            List<String> heroes = HeroConfig.getAvailableNames();
            player.sendMessage(Component.text("Available heroes: " + String.join(", ", heroes), NamedTextColor.AQUA));
            return true;
        }

        String heroName = args[0].toLowerCase();

        // /balance reload — reload all configs from disk
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            TemplateConfig.reload();
            HeroConfig.load();
            player.sendMessage(Component.text("Hero and template configurations reloaded.", NamedTextColor.GREEN));
            return true;
        }

        // Allow "default" as a target to edit default.yml
        if (!heroName.equals("default") && !HeroConfig.exists(heroName)) {
            player.sendMessage(Component.text("Hero not found: " + heroName, NamedTextColor.RED));
            return true;
        }

        // /balance <hero|default> <path...> [value]
        return HeroConfig.handleConfigCommand(player, heroName, Arrays.copyOfRange(args, 1, args.length));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(HeroConfig.getAvailableNames());
            options.add("list");
            options.add("reload");
            options.add("default");
            return filterStartsWith(options, args[0]);
        }

        String heroName = args[0].toLowerCase();
        if (heroName.equals("list") || !HeroConfig.exists(heroName)) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            List<String> options = new ArrayList<>(HeroConfig.getConfigKeys(heroName, ""));
            return filterStartsWith(options, args[1]);
        }

        // Build path from args[1] to args[length-2]
        String path = String.join(".", Arrays.copyOfRange(args, 1, args.length - 1));
        return filterStartsWith(HeroConfig.getConfigKeys(heroName, path), args[args.length - 1]);
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }
}
