package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.MapAdmin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MapAdminCommand extends HeroesCommand {
    public MapAdminCommand() {
        super("mapadmin", "Admin command for managing game maps",
                "/mapadmin <list|create|edit|save|set|get|reload> [name] [property] [index]",
                List.of("ma", "maps", "editmap", "editmaps", "managemaps"), "heroes.mapadmin", true);
    }

    @Override
    public List<String> extraPermissions() {
        return List.of("heroes.mapadmin.map.*", "heroes.mapadmin.create");
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("Usage: " + getUsage());
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> handleListCommand(player);
            case "create" -> handleCreateCommand(player, args);
            case "edit" -> handleEditCommand(player, args);
            case "save" -> handleSaveCommand(player);
            case "get" -> handleGetCommand(player, args);
            case "set" -> handleSetCommand(player, args);
            case "reload" -> handleReloadCommand(player);
            default -> player.sendMessage("Unknown subcommand. Usage: " + getUsage());
        }
    }

    private void handleListCommand(Player player) {
        player.sendMessage("Available maps: " + String.join(", ", GameMapManager.getAllMapNames()));
    }

    // Creates a brand-new map: starter yml (empty supported-modes — /mappool controls entry into
    // rotation), then straight into a freshly generated void template world with a starting
    // platform. Own node, op-default: ops create the shell, then grant the mapper
    // heroes.mapadmin.map.<name> to build it.
    private void handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("heroes.mapadmin.create")) {
            player.sendMessage("You don't have permission to create maps.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage("Usage: /mapadmin create <name>");
            return;
        }
        String name = args[1];
        if (!name.matches("[A-Za-z0-9_]+")) {
            player.sendMessage("Map names must be alphanumeric/underscore (it becomes the file, world folder and permission node).");
            return;
        }
        if (MapAdmin.mapConfigFile(name) != null) {
            player.sendMessage("A map with that name already exists.");
            return;
        }

        World world;
        try {
            world = MapAdmin.createMap(name);
        } catch (IllegalStateException e) {
            player.sendMessage(e.getMessage());
            return;
        }

        player.teleport(world.getSpawnLocation());
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendMessage("Created map '" + name + "' — build away. Configure with /ma set, then /ma save.");
        player.sendMessage("It joins no pools until /mappool <mode> add " + name
                + "; grant builders " + MapAdmin.mapPermissionNode(name) + ".");
    }

    private void handleEditCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /mapadmin edit <mapName>");
            return;
        }
        if (deniedForMap(player, args[1])) return;
        World worldToEdit = MapAdmin.loadTemplateForEditing(args[1]);
        if (worldToEdit != null) {
            player.teleport(worldToEdit.getSpawnLocation());
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage("Now editing template: " + args[1]);
        } else {
            player.sendMessage("Map not found.");
        }
    }

    private void handleSaveCommand(Player player) {
        if (!player.getWorld().getName().startsWith(MapAdmin.TEMPLATES_PREFIX)) {
            player.sendMessage("You are not in a template world.");
            return;
        }
        GameMap map = MapAdmin.templateByWorld(player.getWorld());
        if (map == null) {
            player.sendMessage("No configured map matches template world: " + player.getWorld().getName());
            return;
        }
        if (deniedForMap(player, map.name())) return;
        MapAdmin.saveTemplateWorld(player.getWorld());
        player.sendMessage("World saved. Teleporting to lobby.");
    }

    private void handleGetCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /mapadmin get <mapName> <property> [index]");
            return;
        }
        if (deniedForMap(player, args[1])) return;
        int getIndex = -1;
        if (args.length > 3) {
            try {
                getIndex = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid index: '" + args[3] + "' is not a number.");
                return;
            }
        }
        String propertyValue = MapAdmin.getMapProperty(args[1], args[2], getIndex);
        player.sendMessage(args[2] + ": " + propertyValue);
    }

    private void handleSetCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /mapadmin set <mapName> <property> [args...]");
            return;
        }
        if (deniedForMap(player, args[1])) return;
        MapAdmin.updateMapConfig(player, args[1], args);
    }

    private void handleReloadCommand(Player player) {
        GameMapManager.loadMapsFromConfig();
        player.sendMessage("All map configurations have been reloaded.");
    }

    // Per-map editing gate on top of heroes.mapadmin: heroes.mapadmin.map.<map>. Ops pass through
    // the registered heroes.mapadmin.map.* parent (op default); restricted mappers get explicit
    // per-map grants (or the wildcard via LuckPerms) so a mapper can be limited to exactly the
    // maps they're building. Messages and returns true when denied.
    private boolean deniedForMap(Player player, String mapName) {
        if (canEditMap(player, mapName)) return false;
        player.sendMessage("You don't have access to edit map: " + mapName);
        return true;
    }

    // Also used by tab completion, so restricted mappers only see their own maps.
    private static boolean canEditMap(Player player, String mapName) {
        String node = MapAdmin.mapPermissionNode(mapName);
        // Lazy registration, parented to the op-defaulted wildcard Heroes registers at enable —
        // this is what lets plain ops pass under LuckPerms (LP only honors REGISTERED defaults).
        if (Bukkit.getPluginManager().getPermission(node) == null) {
            Permission child = new Permission(node, PermissionDefault.FALSE);
            child.addParent("heroes.mapadmin.map.*", true);
            Bukkit.getPluginManager().addPermission(child);
        }
        return player.hasPermission(node);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(
                    Arrays.asList("list", "edit", "save", "get", "set", "reload"));
            if (sender.hasPermission("heroes.mapadmin.create")) subs.add("create");
            return TabComplete.startsWith(subs, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            List<String> maps = GameMapManager.getAllMapNames();
            // Restricted mappers only see the maps they can actually edit.
            if (sender instanceof Player p) {
                maps = maps.stream().filter(name -> canEditMap(p, name)).toList();
            }
            return TabComplete.startsWith(maps, args[1]);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("set"))) {
            return TabComplete.startsWith(MapAdmin.EDITABLE_PROPERTIES, args[2]);
        }
        return Collections.emptyList();
    }
}
