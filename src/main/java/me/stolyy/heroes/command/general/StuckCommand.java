package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.physics.WallDetection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StuckCommand extends Command {
    public StuckCommand() {
        super("stuck");
        this.setDescription("Use to get out of a block");
        this.setUsage("/stuck");
        this.setAliases(List.of("s"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        Game game = GameManager.getPlayerGame(player);
        if(game == null){
            Heroes.teleportToLobby(player);
            return true;
        }
        if(isInBlock(player)){
            player.teleport(game.getRespawnLocation(player, null, player.getLocation().clone()));
            return true;
        }
        player.sendMessage("You can only use /stuck when in a block!");
        return true;
    }

    /**
     * True if the player's hitbox is embedded in real block collision geometry.
     * <p>
     * The old hand-rolled scan modelled every solid block as a full cube and started at the FEET
     * block, so merely STANDING on a slab or a stair read as "in a block" and teleported the player
     * across the map — while an actual slab-at-head-level embedding was indistinguishable from it.
     */
    public boolean isInBlock(Player player) {
        return WallDetection.isTrapped(player);
    }
}
