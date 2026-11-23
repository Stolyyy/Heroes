package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.vfx.ui.gui.BossModeSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import me.stolyy.heroes.vfx.ui.gui.EvolutionSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.GUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.IronmanSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.NuzlockeSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.SeriesSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.TdmSettingsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JoinCommand extends Command {
    private static final String USAGE = "/join <1v1|2v2|custom|tdm|evolution|series|nuzlocke|ironman|boss> [map]";

    public JoinCommand() {
        super("join");
        this.setDescription("Join a game");
        this.setUsage(USAGE);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        JoinRequest request = parse(player, args);
        if (request == null) return true;

        perform(player, request);
        return true;
    }

    /** A parsed {@code /join} argument list: mode (preset names route into CUSTOM), preset and map. */
    public record JoinRequest(GameMode gameMode, GameModePreset preset, String mapName) {}

    /** Parses {@code /join} arguments; messages the player and returns null when they are invalid. */
    public static JoinRequest parse(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(USAGE);
            return null;
        }

        // Preset names route into a custom game with that preset pre-applied.
        GameModePreset preset = parsePresetOrNull(args[0]);
        GameMode gameMode;
        if (preset != null) {
            gameMode = GameMode.CUSTOM;
        } else {
            try {
                gameMode = parseGameMode(args[0]);
            } catch (IllegalArgumentException e) {
                player.sendMessage("Invalid game mode. Use 1v1, 2v2, custom, or a preset (tdm, evolution, series, nuzlocke, ironman, boss).");
                return null;
            }
        }

        String mapName = args.length == 2 ? args[1] : null;
        if (mapName != null && gameMode != GameMode.CUSTOM) {
            player.sendMessage("Map selection is only available for custom games.");
            return null;
        }
        return new JoinRequest(gameMode, preset, mapName);
    }

    /** Joins as {@code /join <args>} would. Shared with the party ready check. */
    public static void perform(Player player, JoinRequest request) {
        GameManager.joinGame(player, request.gameMode(), request.mapName());

        GameModePreset preset = request.preset();
        if (preset != null) {
            Game game = GameManager.getPlayerGame(player);
            if (game == null || game.gameState() != GameState.WAITING) return; // join failed; already messaged
            game.setGameModePreset(preset);
            CustomGameGUI partyGUI = new CustomGameGUI(game, player);
            GUI settings = switch (preset) {
                case BOSS -> new BossModeSettingsGUI(game, player, partyGUI);
                case TDM -> new TdmSettingsGUI(game, player, partyGUI);
                case EVOLUTION -> new EvolutionSettingsGUI(game, player, partyGUI);
                case SERIES -> new SeriesSettingsGUI(game, player, partyGUI);
                case NUZLOCKE -> new NuzlockeSettingsGUI(game, player, partyGUI);
                case IRONMAN -> new IronmanSettingsGUI(game, player, partyGUI);
                case DEFAULT -> partyGUI;
            };
            GUIManager.open(player, settings);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return Stream.of("1v1", "2v2", "custom", "tdm", "evolution", "series", "nuzlocke", "ironman", "boss")
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (parseGameModeOrNull(args[0]) == GameMode.CUSTOM || parsePresetOrNull(args[0]) != null)) {
            String prefix = args[1].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String name : GameMapManager.getAllMapNames()) {
                if (name.toLowerCase().startsWith(prefix)) matches.add(name);
            }
            return matches;
        }
        return Collections.emptyList();
    }

    private static GameMode parseGameMode(String input) {
        return switch (input.toLowerCase()) {
            case "1v1" -> GameMode.ONE_V_ONE;
            case "2v2" -> GameMode.TWO_V_TWO;
            case "custom", "party" -> GameMode.CUSTOM;
            default -> throw new IllegalArgumentException("Invalid game mode");
        };
    }

    private GameMode parseGameModeOrNull(String input) {
        try {
            return parseGameMode(input);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static GameModePreset parsePresetOrNull(String input) {
        return switch (input.toLowerCase()) {
            case "tdm" -> GameModePreset.TDM;
            case "evolution", "evo" -> GameModePreset.EVOLUTION;
            case "series" -> GameModePreset.SERIES;
            case "nuzlocke" -> GameModePreset.NUZLOCKE;
            case "ironman" -> GameModePreset.IRONMAN;
            case "boss" -> GameModePreset.BOSS;
            default -> null;
        };
    }
}
