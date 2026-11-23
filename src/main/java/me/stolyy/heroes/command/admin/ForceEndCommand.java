package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameEndReason;
import me.stolyy.heroes.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Force-ends the sender's game, discarding its result entirely (see
 * {@link GameEndReason#CANCELLED}). The escape hatch for a match that has wedged itself —
 * without it the only way out of a stuck game is a server restart.
 */
public class ForceEndCommand extends Command {

    public ForceEndCommand() {
        super("forceend");
        this.setDescription("Admin command to force-end the game you are in");
        this.setUsage("/forceend");
        this.setPermission("heroes.forceend");
        this.setAliases(List.of("fe", "endgame"));
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

        // Spectators are roster members, so an admin who /spectate'd into the stuck game can end
        // it without having to be a participant.
        Game game = GameManager.getPlayerGame(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game.", NamedTextColor.RED));
            return true;
        }

        game.end(GameEndReason.CANCELLED);
        player.sendMessage(Component.text("Game force-ended — no stats, XP or elo were recorded.",
                NamedTextColor.GREEN));
        return true;
    }
}
