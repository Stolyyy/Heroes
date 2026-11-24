package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.rules.GameModePreset;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class JoinCommand extends HeroesCommand {
    private static final String USAGE = "/join <1v1|2v2|custom|tdm|evolution|series|nuzlocke|ironman|boss> [map]";

    public JoinCommand() {
        super("join", "Join a game", USAGE, List.of("queue", "q", "play"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        JoinRequest request = parse(player, args);
        if (request == null) return;
        perform(player, request);
    }

    /** A parsed {@code /join} argument list: mode (preset names route into CUSTOM), preset and map. */
    public record JoinRequest(GameMode gameMode, GameModePreset preset, String mapName) {}

    /** Parses {@code /join} arguments; messages the player and returns null when they are invalid. */
    public static JoinRequest parse(Player player, String[] args) {
        if (args.length < 1 || args.length > 2) {
            player.sendMessage(USAGE);
            return null;
        }

        GameModePreset preset = GameModePreset.fromToken(args[0]).orElse(null);
        GameMode gameMode;
        if (preset != null) {
            gameMode = GameMode.CUSTOM;
        } else {
            gameMode = GameMode.fromToken(args[0]).orElse(null);
            if (gameMode == null) {
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
        GameManager.joinWithPreset(player, request.gameMode(), request.preset(), request.mapName());
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.startsWith(
                    Stream.concat(GameMode.tokens().stream(), GameModePreset.commandTokens().stream()),
                    args[0]);
        }
        if (args.length == 2 && (GameMode.fromToken(args[0]).orElse(null) == GameMode.CUSTOM
                || GameModePreset.fromToken(args[0]).isPresent())) {
            return TabComplete.startsWith(GameMapManager.getAllMapNames(), args[1]);
        }
        return Collections.emptyList();
    }
}
