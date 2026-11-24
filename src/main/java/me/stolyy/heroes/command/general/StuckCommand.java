package me.stolyy.heroes.command.general;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.game.GameManager;
import org.bukkit.entity.Player;

import java.util.List;

public class StuckCommand extends HeroesCommand {
    public StuckCommand() {
        super("stuck", "Use to get out of a block", "/stuck", List.of("s"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (GameManager.getPlayerGame(player) == null) {
            Heroes.teleportToLobby(player);
            return;
        }
        if (!GameManager.unstuck(player)) {
            player.sendMessage("You can only use /stuck when in a block!");
        }
    }
}
