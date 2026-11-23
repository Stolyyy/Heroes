package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.SpectateGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class SpectateCommand extends Command {

    public SpectateCommand() {
        super("spectate");
        this.setDescription("Spectate a player's game");
        this.setUsage("/spectate [player]");
        this.setAliases(List.of("spec"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // No args -> Open SpectateGUI
        if (args.length == 0) {
            GUIManager.open(player, new SpectateGUI(player));
            return true;
        }

        // With player arg
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return true;
        }

        if (!GameManager.isPlayerInGame(target)) {
            player.sendMessage(Component.text("That player is not in a game.", NamedTextColor.RED));
            return true;
        }

        GameManager.spectateGame(player, target);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(GameManager::isPlayerInGame)
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
