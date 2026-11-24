package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.SpectateGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class SpectateCommand extends HeroesCommand {

    public SpectateCommand() {
        super("spectate", "Spectate a player's game",
                "/spectate [player]", List.of("spec"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            GUIManager.open(player, new SpectateGUI(player));
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return;
        }

        if (!GameManager.isPlayerInGame(target)) {
            player.sendMessage(Component.text("That player is not in a game.", NamedTextColor.RED));
            return;
        }

        GameManager.spectateGame(player, target);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.onlinePlayers(args[0], GameManager::isPlayerInGame);
        }
        return Collections.emptyList();
    }
}
