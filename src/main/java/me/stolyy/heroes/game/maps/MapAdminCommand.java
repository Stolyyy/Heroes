package me.stolyy.heroes.game.maps;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MapAdminCommand extends Command {
    public MapAdminCommand() {
        super("mapadmin");
        this.setDescription("Admin command for managing game maps");
        this.setUsage("/mapadmin <list|edit|save|set|get|reload> [name] [property] [index]");
        this.setPermission("heroes.mapadmin");
        this.setPermissionMessage("You do not have permission to use this command.");
        this.setAliases(List.of("ma", "maps", "editmap", "editmaps", "managemaps"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Usage: " + getUsage());
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list":
                player.sendMessage("Available maps: " + String.join(", ", GameMapManager.getTemplateMapNames()));
                break;

            case "edit":
                if (args.length < 2) {
                    player.sendMessage("Usage: /mapadmin edit <mapName>");
                    return true;
                }
                World worldToEdit = GameMapManager.loadTemplateForEditing(args[1]);
                if (worldToEdit != null) {
                    player.teleport(worldToEdit.getSpawnLocation());
                    player.setGameMode(org.bukkit.GameMode.CREATIVE);
                    player.sendMessage("Now editing template: " + args[1]);
                } else {
                    player.sendMessage("Map not found.");
                }
                break;

            case "save":
                if (player.getWorld().getName().startsWith("templates/")) {
                    GameMapManager.saveTemplateWorld(player.getWorld());
                    player.sendMessage("World saved. Teleporting to lobby.");
                } else {
                    player.sendMessage("You are not in a template world.");
                }
                break;

            case "get":
                if (args.length < 3) {
                    player.sendMessage("Usage: /mapadmin get <mapName> <property> [index]");
                    return true;
                }
                int getIndex = (args.length > 3) ? Integer.parseInt(args[3]) : -1;
                String propertyValue = GameMapManager.getMapProperty(args[1], args[2], getIndex);
                player.sendMessage(args[2] + ": " + propertyValue);
                break;

            case "set":
                if (args.length < 3) {
                    player.sendMessage("Usage: /mapadmin set <mapName> <property> [args...]");
                    return true;
                }
                boolean success = GameMapManager.updateMapConfig(player, args[1], args);
                if (success) {
                    if (!args[2].equalsIgnoreCase("boundary1")) {
                        player.sendMessage("Configuration for '" + args[1] + "' updated successfully.");
                    }
                }
                break;

            case "reload":
                GameMapManager.loadMapsFromConfig();
                player.sendMessage("All map configurations have been reloaded.");
                break;

            default:
                player.sendMessage("Unknown subcommand. Usage: " + getUsage());
                break;
        }

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        if (args.length == 1) {
            return Arrays.asList("list", "edit", "save", "get", "set", "reload");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            return GameMapManager.getTemplateMapNames();
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            return Arrays.asList("spectatorlocation", "spawnlocation", "crystallocation", "boundaries", "boundary1", "boundary2");
        }
        return Collections.emptyList();
    }
}
