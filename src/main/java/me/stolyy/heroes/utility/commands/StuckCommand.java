package me.stolyy.heroes.utility.commands;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.Heroes;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
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
            player.teleport(game.getRespawnLocation(player));
            return true;
        }
        player.sendMessage("You can only use /stuck when in a block!");
        return true;
    }

    //replace with wall detection
    public boolean isInBlock(Player player) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        double minX = x - 0.3;
        double maxX = x + 0.3;
        double maxY = y + 1.8;
        double minZ = z - 0.3;
        double maxZ = z + 0.3;

        for (int bx = (int)Math.floor(minX); bx <= (int)Math.floor(maxX); bx++) {
            for (int by = (int)Math.floor(y); by <= (int)Math.floor(maxY); by++) {
                for (int bz = (int)Math.floor(minZ); bz <= (int)Math.floor(maxZ); bz++) {
                    Block block = world.getBlockAt(bx, by, bz);
                    if (block.getType().isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
