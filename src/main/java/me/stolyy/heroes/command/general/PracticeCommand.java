package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.PracticeArena;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/** /practice — toggles the lobby practice range. */
public class PracticeCommand extends HeroesCommand {

    public PracticeCommand() {
        super("practice", "Enter or leave the practice range",
                "/practice [arena]", List.of("range", "train"));
    }

    @Override
    protected void execute(Player player, String[] args) {
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
                return;
            }
            PracticeManager.enterPractice(player, arena);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) return List.of();
        return TabComplete.startsWith(
                GameConfigManager.getPracticeArenas().stream().map(PracticeArena::name),
                args[0]);
    }
}
