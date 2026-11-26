package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameEndReason;
import me.stolyy.heroes.game.GameManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Force-ends the sender's game, discarding its result entirely (see
 * {@link GameEndReason#CANCELLED}). The escape hatch for a match that has wedged itself —
 * without it the only way out of a stuck game is a server restart.
 */
public class ForceEndCommand extends HeroesCommand {

    public ForceEndCommand() {
        super("forceend", "Admin command to force-end the game you are in",
                "/forceend", List.of("fe", "endgame"), "heroes.forceend", true);
    }

    @Override
    protected void execute(Player player, String[] args) {
        // Spectators are roster members, so an admin who /spectate'd into the stuck game can end
        // it without having to be a participant.
        Game game = GameManager.getPlayerGame(player);
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game.", NamedTextColor.RED));
            return;
        }

        game.end(GameEndReason.CANCELLED);
        player.sendMessage(Component.text("Game force-ended — no stats, XP or elo were recorded.",
                NamedTextColor.GREEN));
    }
}
