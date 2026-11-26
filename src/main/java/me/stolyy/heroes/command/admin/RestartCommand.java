package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.RestartManager;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class RestartCommand extends HeroesCommand {

    public RestartCommand() {
        super("heroesrestart", "Admin command for restarting the server gracefully",
                "/heroesrestart <now|drain>", List.of("hrestart"), "heroes.restart", false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length != 1 || !(args[0].equalsIgnoreCase("now") || args[0].equalsIgnoreCase("drain"))) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        if (args[0].equalsIgnoreCase("drain")) {
            if (RestartManager.isDraining()) {
                sender.sendMessage(GameConfigManager.getMessage("restart.admin-already"));
                return;
            }
            RestartManager.startDrain();
            sender.sendMessage(GameConfigManager.getMessage("restart.admin-drain-started"));
        } else {
            if (RestartManager.isCountingDown()) {
                sender.sendMessage(GameConfigManager.getMessage("restart.admin-already"));
                return;
            }
            RestartManager.startNow();
            sender.sendMessage(GameConfigManager.getMessage("restart.admin-now-started"));
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        return TabComplete.startsWith(List.of("now", "drain"), args[0]);
    }
}
