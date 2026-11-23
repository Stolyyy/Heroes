package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.game.map.GameMapManager;
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

        return switch (subCommand) {
            case "list" -> handleListCommand(player);
            case "create" -> handleCreateCommand(player, args);
            case "edit" -> handleEditCommand(player, args);
            case "save" -> handleSaveCommand(player);
            case "get" -> handleGetCommand(player, args);
            case "set" -> handleSetCommand(player, args);
            case "reload" -> handleReloadCommand(player);
            default -> handleUnknownCommand(player);
        };
    }

    private boolean handleListCommand(Player player) {
        player.sendMessage("Available maps: " + String.join(", ", GameMapManager.getTemplateMapNames()));
        return true;
    }

    /**
     * Creates a brand-new map: starter yml (empty supported-modes — /mappool controls entry into
     * rotation), then straight into a freshly generated void template world with a starting
     * platform. Own node, op-default: ops create the shell, then grant the mapper
     * heroes.mapadmin.map.&lt;name&gt; to build it.
     */
    private boolean handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("heroes.mapadmin.create")) {
            player.sendMessage("You don't have permission to create maps.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /mapadmin create <name>");
            return true;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendMessage("Map names must be alphanumeric/underscore (it becomes the file, world folder and permission node).");
            return true;
        }
        if (GameMapManager.mapConfigFile(name) != null) {
            player.sendMessage("A map with that name already exists.");
            return true;
        }

        try {
            GameMapManager.createMapConfig(name);
        } catch (IllegalStateException e) {
            player.sendMessage(e.getMessage());
            return true;
        }
        GameMapManager.reloadMap(name);

        World world = GameMapManager.loadTemplateForEditing(name);
        if (world == null) {
            player.sendMessage("Config created, but the template world failed to load — check console.");
            return true;
        }
        // A fresh void world has nowhere to stand: one starter block, spawn on top, flight on.
        world.getBlockAt(0, 64, 0).setType(org.bukkit.Material.STONE);
        world.setSpawnLocation(0, 65, 0);
        player.teleport(world.getSpawnLocation());
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendMessage("Created map '" + name + "' — build away. Configure with /ma set, then /ma save.");
        player.sendMessage("It joins no pools until /mappool <mode> add " + name
                + "; grant builders heroes.mapadmin.map." + name.toLowerCase() + ".");
        return true;
    }

    private boolean handleEditCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /mapadmin edit <mapName>");
            return true;
        }
        if (deniedForMap(player, args[1])) return true;
        World worldToEdit = GameMapManager.loadTemplateForEditing(args[1]);
        if (worldToEdit != null) {
            player.teleport(worldToEdit.getSpawnLocation());
            player.setGameMode(org.bukkit.GameMode.CREATIVE);
            player.sendMessage("Now editing template: " + args[1]);
        } else {
            player.sendMessage("Map not found.");
        }
        return true;
    }

    private boolean handleSaveCommand(Player player) {
        if (player.getWorld().getName().startsWith("templates/")) {
            me.stolyy.heroes.game.map.GameMap map = GameMapManager.templateByWorld(player.getWorld());
            if (map != null && deniedForMap(player, map.name())) return true;
            GameMapManager.saveTemplateWorld(player.getWorld());
            player.sendMessage("World saved. Teleporting to lobby.");
        } else {
            player.sendMessage("You are not in a template world.");
        }
        return true;
    }

    private boolean handleGetCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /mapadmin get <mapName> <property> [index]");
            return true;
        }
        if (deniedForMap(player, args[1])) return true;
        int getIndex;
        if (args.length > 3) {
            try {
                getIndex = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid index: '" + args[3] + "' is not a number.");
                return true;
            }
        } else {
            getIndex = -1;
        }
        String propertyValue = GameMapManager.getMapProperty(args[1], args[2], getIndex);
        player.sendMessage(args[2] + ": " + propertyValue);
        return true;
    }

    private boolean handleSetCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /mapadmin set <mapName> <property> [args...]");
            return true;
        }
        if (deniedForMap(player, args[1])) return true;
        boolean success = GameMapManager.updateMapConfig(player, args[1], args);
        if (success) {
            if (!args[2].equalsIgnoreCase("boundary1")) {
                player.sendMessage("Configuration for '" + args[1] + "' updated successfully.");
            }
        }
        return true;
    }

    private boolean handleReloadCommand(Player player) {
        GameMapManager.loadMapsFromConfig();
        player.sendMessage("All map configurations have been reloaded.");
        return true;
    }

    private boolean handleUnknownCommand(Player player) {
        player.sendMessage("Unknown subcommand. Usage: " + getUsage());
        return true;
    }

    /**
     * Per-map editing gate on top of heroes.mapadmin: {@code heroes.mapadmin.map.<map>}. Ops pass
     * through the registered {@code heroes.mapadmin.map.*} parent (op default); restricted mappers
     * get explicit per-map grants (or the wildcard via LuckPerms) — so a mapper can be limited to
     * exactly the maps they're building. Messages and returns true when denied.
     */
    private boolean deniedForMap(Player player, String mapName) {
        if (canEditMap(player, mapName)) return false;
        player.sendMessage("You don't have access to edit map: " + mapName);
        return true;
    }

    /** Also used by tab completion, so restricted mappers only see their own maps. */
    private static boolean canEditMap(Player player, String mapName) {
        String node = "heroes.mapadmin.map." + mapName.toLowerCase().replace(" ", "");
        // Lazy registration, parented to the op-defaulted wildcard Heroes registers at enable —
        // this is what lets plain ops pass under LuckPerms (LP only honors REGISTERED defaults).
        if (org.bukkit.Bukkit.getPluginManager().getPermission(node) == null) {
            org.bukkit.permissions.Permission child = new org.bukkit.permissions.Permission(
                    node, org.bukkit.permissions.PermissionDefault.FALSE);
            child.addParent("heroes.mapadmin.map.*", true);
            org.bukkit.Bukkit.getPluginManager().addPermission(child);
        }
        return player.hasPermission(node);
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) throws IllegalArgumentException {
        // Every position filters by the partial arg — unfiltered lists ignore what's been typed.
        if (args.length == 1) {
            List<String> subs = new java.util.ArrayList<>(
                    Arrays.asList("list", "edit", "save", "get", "set", "reload"));
            if (sender.hasPermission("heroes.mapadmin.create")) subs.add("create");
            return filterStartsWith(subs, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            List<String> maps = GameMapManager.getTemplateMapNames();
            // Restricted mappers only see the maps they can actually edit.
            if (sender instanceof Player p) {
                maps = maps.stream().filter(name -> canEditMap(p, name)).toList();
            }
            return filterStartsWith(maps, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            return filterStartsWith(Arrays.asList("spectatorlocation", "spawnlocation", "crystallocation", "boundaries", "boundary1", "boundary2"), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .toList();
    }
}
