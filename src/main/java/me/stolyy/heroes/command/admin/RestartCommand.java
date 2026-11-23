package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.game.RestartManager;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RestartCommand extends Command {

    public RestartCommand() {
        super("restart");
        this.setDescription("Admin command for restarting the server gracefully");
        this.setUsage("/restart <now|drain>");
        this.setPermission("heroes.restart");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1 || !(args[0].equalsIgnoreCase("now") || args[0].equalsIgnoreCase("drain"))) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return true;
        }

        if (args[0].equalsIgnoreCase("drain")) {
            if (RestartManager.isDraining()) {
                sender.sendMessage(GameConfigManager.getMessage("restart.admin-already"));
                return true;
            }
            RestartManager.startDrain();
            sender.sendMessage(GameConfigManager.getMessage("restart.admin-drain-started"));
        } else {
            if (RestartManager.isCountingDown()) {
                sender.sendMessage(GameConfigManager.getMessage("restart.admin-already"));
                return true;
            }
            RestartManager.startNow();
            sender.sendMessage(GameConfigManager.getMessage("restart.admin-now-started"));
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        return List.of("now", "drain").stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
    }
}
