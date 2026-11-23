package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.util.YmlSurgeon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-mode map pool management: {@code /mappool <1v1|2v2|custom[,...]> <add|remove|list> [map]},
 * plus {@code /mappool list} (every map with its modes) and {@code /mappool diff <modeA> <modeB>}.
 *
 * <p>A mode's pool IS the union of every map yml whose {@code supported-modes} lists it — the
 * same data that drives queue map rotation and the custom map GUI. add/remove edit that list in
 * the map's yml (comment-preserving, via {@link YmlSurgeon}) and hot-swap just that one map via
 * {@link GameMapManager#reloadMap}.
 */
public class MapPoolCommand extends Command {
    private static final List<String> MODE_TOKENS = List.of("1v1", "2v2", "custom");

    public MapPoolCommand() {
        super("mappool");
        this.setDescription("Manage which maps are in each game mode's pool");
        this.setUsage("/mappool <list | diff <modeA> <modeB> | <mode[,mode...]> <add|remove|list> [map]>");
        // Own node, NOT heroes.mapadmin: mappers build maps, but only ops decide what the
        // ranked/custom pools actually serve.
        this.setPermission("heroes.mappool");
        this.setAliases(List.of("mp", "map_pool"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            listAll(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("diff")) {
            diff(sender, args);
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return true;
        }

        List<GameMode> modes = parseModes(sender, args[0]);
        if (modes == null) return true;

        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            for (GameMode mode : modes) {
                List<String> names = GameMapManager.getMapsForMode(mode).stream().map(GameMap::name).sorted().toList();
                sender.sendMessage(Component.text(mode.displayName() + " pool (" + names.size() + "): "
                        + String.join(", ", names), NamedTextColor.AQUA));
            }
            return true;
        }

        if (!action.equals("add") && !action.equals("remove")) {
            sender.sendMessage(Component.text("Unknown action: " + args[1] + " (add, remove, list)", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mappool " + args[0] + " " + action + " <map>", NamedTextColor.YELLOW));
            return true;
        }

        String mapName = args[2];
        File mapFile = GameMapManager.mapConfigFile(mapName);
        if (mapFile == null) {
            sender.sendMessage(Component.text("Map not found: " + mapName, NamedTextColor.RED));
            return true;
        }

        boolean changed = false;
        for (GameMode mode : modes) {
            try {
                if (action.equals("add")) {
                    YmlSurgeon.addListEntry(mapFile, new String[]{"supported-modes"}, mode.name());
                } else {
                    YmlSurgeon.removeListEntry(mapFile, new String[]{"supported-modes"}, mode.name());
                }
            } catch (IllegalStateException e) {
                // Carries the exact reason (already in pool / not in pool / structure mismatch).
                sender.sendMessage(Component.text(mode.displayName() + ": " + e.getMessage(), NamedTextColor.RED));
                continue;
            }
            changed = true;
            sender.sendMessage(Component.text((action.equals("add") ? "Added " : "Removed ") + mapName
                    + (action.equals("add") ? " to " : " from ") + mode.displayName() + " pool.", NamedTextColor.GREEN));
        }
        if (changed) GameMapManager.reloadMap(mapName);
        return true;
    }

    /** One line per map: its name, then each mode green when the map is in that pool and dark gray when not. */
    private void listAll(CommandSender sender) {
        List<String> names = GameMapManager.getAllMapNames();
        sender.sendMessage(Component.text("Maps and their pools (" + names.size() + "):", NamedTextColor.AQUA));
        for (String name : names) {
            GameMap map = GameMapManager.findMapByName(name);
            Component line = Component.text(name + "  ", NamedTextColor.WHITE);
            for (GameMode mode : GameMode.values()) {
                boolean present = map.supportedModes().contains(mode);
                line = line.append(Component.text(shortName(mode) + " ",
                        present ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            }
            sender.sendMessage(line);
        }
    }

    private void diff(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mappool diff <modeA> <modeB>", NamedTextColor.YELLOW));
            return;
        }
        GameMode a = parseMode(args[1]);
        GameMode b = parseMode(args[2]);
        if (a == null || b == null) {
            sender.sendMessage(Component.text("Unknown mode: " + (a == null ? args[1] : args[2])
                    + " (1v1, 2v2, custom)", NamedTextColor.RED));
            return;
        }
        List<String> poolA = GameMapManager.getMapsForMode(a).stream().map(GameMap::name).sorted().toList();
        List<String> poolB = GameMapManager.getMapsForMode(b).stream().map(GameMap::name).sorted().toList();
        sender.sendMessage(Component.text("In " + a.displayName() + " not " + b.displayName() + ": "
                + joinOrNone(poolA.stream().filter(n -> !poolB.contains(n)).toList()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("In " + b.displayName() + " not " + a.displayName() + ": "
                + joinOrNone(poolB.stream().filter(n -> !poolA.contains(n)).toList()), NamedTextColor.AQUA));
    }

    private static String joinOrNone(List<String> names) {
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }

    private static String shortName(GameMode mode) {
        return switch (mode) {
            case ONE_V_ONE -> "1v1";
            case TWO_V_TWO -> "2v2";
            case CUSTOM -> "custom";
        };
    }

    /** Parses a comma-separated mode list; sends the error and returns null on the first unknown token. */
    private static List<GameMode> parseModes(CommandSender sender, String token) {
        List<GameMode> modes = new ArrayList<>();
        for (String part : token.split(",")) {
            GameMode mode = parseMode(part);
            if (mode == null) {
                sender.sendMessage(Component.text("Unknown mode: " + part + " (1v1, 2v2, custom)", NamedTextColor.RED));
                return null;
            }
            if (!modes.contains(mode)) modes.add(mode);
        }
        return modes;
    }

    private static GameMode parseMode(String token) {
        return switch (token.toLowerCase()) {
            case "1v1", "one_v_one" -> GameMode.ONE_V_ONE;
            case "2v2", "two_v_two" -> GameMode.TWO_V_TWO;
            case "custom" -> GameMode.CUSTOM;
            default -> null;
        };
    }

    private static boolean inPool(GameMode mode, String mapName) {
        return GameMapManager.getMapsForMode(mode).stream().anyMatch(m -> m.name().equals(mapName));
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            // A comma list completes its last token and keeps the prefix already typed.
            int comma = args[0].lastIndexOf(',');
            if (comma >= 0) {
                String head = args[0].substring(0, comma + 1);
                return filterStartsWith(MODE_TOKENS, args[0].substring(comma + 1)).stream().map(m -> head + m).toList();
            }
            return filterStartsWith(List.of("1v1", "2v2", "custom", "list", "diff"), args[0]);
        }
        if (args[0].equalsIgnoreCase("diff")) {
            return args.length <= 3 ? filterStartsWith(MODE_TOKENS, args[args.length - 1]) : Collections.emptyList();
        }
        if (args.length == 2) {
            return filterStartsWith(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 3) {
            List<GameMode> modes = new ArrayList<>();
            for (String part : args[0].split(",")) {
                GameMode mode = parseMode(part);
                if (mode == null) return Collections.emptyList();
                modes.add(mode);
            }
            // add offers maps missing from at least one named pool; remove offers maps in at least one.
            boolean add = args[1].equalsIgnoreCase("add");
            List<String> options = GameMapManager.getTemplateMapNames().stream()
                    .filter(name -> add
                            ? !modes.stream().allMatch(m -> inPool(m, name))
                            : modes.stream().anyMatch(m -> inPool(m, name)))
                    .toList();
            return filterStartsWith(options, args[2]);
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
