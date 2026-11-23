package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.PracticeArena;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

/** /practice — toggles the lobby practice range (B24). */
public class PracticeCommand extends Command {

    public PracticeCommand() {
        super("practice");
        this.setDescription("Enter or leave the practice range");
        this.setUsage("/practice [arena]");
        this.setAliases(List.of("range", "train"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (PracticeManager.isPracticing(player)) {
            PracticeManager.exitPractice(player, true);
        } else {
            PracticeArena arena = args.length > 0
                    ? GameConfigManager.getPracticeArena(args[0])
                    : PracticeManager.nearestArena(player);
            if (arena == null) {
                player.sendMessage(Component.text("Unknown practice arena: " + args[0] + " ("
                        + GameConfigManager.getPracticeArenas().stream().map(PracticeArena::name).collect(Collectors.joining(", "))
                        + ")", NamedTextColor.RED));
                return true;
            }
            PracticeManager.enterPractice(player, arena);
        }
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length != 1) return List.of();
        return GameConfigManager.getPracticeArenas().stream()
                .map(PracticeArena::name)
                .filter(n -> n.toLowerCase().startsWith(args[0].toLowerCase()))
                .toList();
    }
}
