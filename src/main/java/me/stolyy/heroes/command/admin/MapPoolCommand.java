package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.MapAdmin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Per-mode map pool management: {@code /mappool <1v1|2v2|custom[,...]> <add|remove|list> [map]},
 * plus {@code /mappool list} (every map with its modes) and {@code /mappool diff <modeA> <modeB>}.
 */
public class MapPoolCommand extends HeroesCommand {

    public MapPoolCommand() {
        super("mappool", "Manage which maps are in each game mode's pool",
                "/mappool <list | diff <modeA> <modeB> | <mode[,mode...]> <add|remove|list> [map]>",
                List.of("mp", "map_pool"), "heroes.mappool", false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            listAll(sender);
            return;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("diff")) {
            diff(sender, args);
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        List<GameMode> modes = parseModes(sender, args[0]);
        if (modes == null) return;

        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            for (GameMode mode : modes) {
                List<String> names = GameMapManager.getMapsForMode(mode).stream().map(GameMap::name).sorted().toList();
                sender.sendMessage(Component.text(mode.displayName() + " pool (" + names.size() + "): "
                        + String.join(", ", names), NamedTextColor.AQUA));
            }
            return;
        }

        if (!action.equals("add") && !action.equals("remove")) {
            sender.sendMessage(Component.text("Unknown action: " + args[1] + " (add, remove, list)", NamedTextColor.RED));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /mappool " + args[0] + " " + action + " <map>", NamedTextColor.YELLOW));
            return;
        }

        String mapName = args[2];
        if (MapAdmin.mapConfigFile(mapName) == null) {
            sender.sendMessage(Component.text("Map not found: " + mapName, NamedTextColor.RED));
            return;
        }

        boolean add = action.equals("add");
        for (MapAdmin.PoolChange result : MapAdmin.setPoolMembership(mapName, modes, add)) {
            if (result.changed()) {
                sender.sendMessage(Component.text((add ? "Added " : "Removed ") + mapName
                        + (add ? " to " : " from ") + result.mode().displayName() + " pool.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text(result.mode().displayName() + ": " + result.error(), NamedTextColor.RED));
            }
        }
    }

    private void listAll(CommandSender sender) {
        List<String> names = GameMapManager.getAllMapNames();
        sender.sendMessage(Component.text("Maps and their pools (" + names.size() + "):", NamedTextColor.AQUA));
        for (String name : names) {
            GameMap map = GameMapManager.findMapByName(name);
            Component line = Component.text(name + "  ", NamedTextColor.WHITE);
            for (GameMode mode : GameMode.values()) {
                boolean present = map.supportedModes().contains(mode);
                line = line.append(Component.text(mode.token() + " ",
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
        Optional<GameMode> a = GameMode.fromToken(args[1]);
        Optional<GameMode> b = GameMode.fromToken(args[2]);
        if (a.isEmpty() || b.isEmpty()) {
            sender.sendMessage(Component.text("Unknown mode: " + (a.isEmpty() ? args[1] : args[2])
                    + " (1v1, 2v2, custom)", NamedTextColor.RED));
            return;
        }
        List<String> poolA = GameMapManager.getMapsForMode(a.get()).stream().map(GameMap::name).sorted().toList();
        List<String> poolB = GameMapManager.getMapsForMode(b.get()).stream().map(GameMap::name).sorted().toList();
        sender.sendMessage(Component.text("In " + a.get().displayName() + " not " + b.get().displayName() + ": "
                + joinOrNone(poolA.stream().filter(n -> !poolB.contains(n)).toList()), NamedTextColor.AQUA));
        sender.sendMessage(Component.text("In " + b.get().displayName() + " not " + a.get().displayName() + ": "
                + joinOrNone(poolB.stream().filter(n -> !poolA.contains(n)).toList()), NamedTextColor.AQUA));
    }

    private static String joinOrNone(List<String> names) {
        return names.isEmpty() ? "(none)" : String.join(", ", names);
    }

    private static List<GameMode> parseModes(CommandSender sender, String token) {
        List<GameMode> modes = new ArrayList<>();
        for (String part : token.split(",")) {
            Optional<GameMode> parsed = GameMode.fromToken(part);
            if (parsed.isEmpty()) {
                sender.sendMessage(Component.text("Unknown mode: " + part + " (1v1, 2v2, custom)", NamedTextColor.RED));
                return null;
            }
            if (!modes.contains(parsed.get())) modes.add(parsed.get());
        }
        return modes;
    }

    private static boolean inPool(GameMode mode, String mapName) {
        return GameMapManager.getMapsForMode(mode).stream().anyMatch(m -> m.name().equals(mapName));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // A comma list completes its last token and keeps the prefix already typed.
            int comma = args[0].lastIndexOf(',');
            if (comma >= 0) {
                String head = args[0].substring(0, comma + 1);
                return TabComplete.startsWith(GameMode.tokens(), args[0].substring(comma + 1))
                        .stream().map(m -> head + m).toList();
            }
            return TabComplete.startsWith(List.of("1v1", "2v2", "custom", "list", "diff"), args[0]);
        }
        if (args[0].equalsIgnoreCase("diff")) {
            return args.length <= 3 ? TabComplete.startsWith(GameMode.tokens(), args[args.length - 1]) : Collections.emptyList();
        }
        if (args.length == 2) {
            return TabComplete.startsWith(List.of("add", "remove", "list"), args[1]);
        }
        if (args.length == 3) {
            List<GameMode> modes = new ArrayList<>();
            for (String part : args[0].split(",")) {
                Optional<GameMode> parsed = GameMode.fromToken(part);
                if (parsed.isEmpty()) return Collections.emptyList();
                modes.add(parsed.get());
            }
            // add offers maps missing from at least one named pool; remove offers maps in at least one.
            boolean add = args[1].equalsIgnoreCase("add");
            List<String> options = GameMapManager.getAllMapNames().stream()
                    .filter(name -> add
                            ? !modes.stream().allMatch(m -> inPool(m, name))
                            : modes.stream().anyMatch(m -> inPool(m, name)))
                    .toList();
            return TabComplete.startsWith(options, args[2]);
        }
        return Collections.emptyList();
    }
}
