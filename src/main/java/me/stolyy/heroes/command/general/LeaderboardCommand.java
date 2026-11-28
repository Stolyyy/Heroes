package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.PlayerStatsManager.Stat;
import me.stolyy.heroes.progression.StatsFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

public class LeaderboardCommand extends HeroesCommand {
    private static final int ENTRIES_PER_PAGE = 10;

    public LeaderboardCommand() {
        super("leaderboard", "View ELO, level and stat leaderboard rankings",
                "/leaderboard [1v1|2v2|level|kills|deaths|wins|games] [hero|1v1|2v2|all] [page]",
                List.of("lb", "elo"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        GameMode mode = null;
        Stat stat = null;
        boolean levelBoard = false;
        String hero = null;
        int page = 1;

        for (String arg : args) {
            Optional<GameMode> parsedMode = GameMode.fromToken(arg).filter(GameMode::hasElo);
            if (parsedMode.isPresent()) {
                mode = parsedMode.get();
                continue;
            }
            Optional<Stat> parsedStat = Stat.fromToken(arg);
            if (parsedStat.isPresent()) {
                stat = parsedStat.get();
                continue;
            }
            String lower = arg.toLowerCase(Locale.ROOT);
            if (lower.equals("all")) {
                mode = null;
            } else if (lower.equals("level") || lower.equals("levels")) {
                levelBoard = true;
            } else if (HeroConfig.exists(lower)) {
                // A hero filter only makes sense on the level board — imply it so "/lb karakot" works.
                hero = lower;
                levelBoard = true;
            } else {
                try {
                    page = Math.max(1, Integer.parseInt(arg));
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.RED));
                    return;
                }
            }
        }

        boolean statBoard = !levelBoard && stat != null;
        if (!levelBoard && !statBoard && mode == null) mode = GameMode.ONE_V_ONE;

        List<PlayerStatsManager.LeaderboardEntry> entries = StatsFormat.queryLeaderboard(
                levelBoard, hero, statBoard ? stat : null, mode, page * ENTRIES_PER_PAGE);
        int totalEntries = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / ENTRIES_PER_PAGE));
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, totalEntries);

        player.sendMessage(Component.text("=== " + StatsFormat.leaderboardTitle(levelBoard, hero, statBoard ? stat : null, mode)
                + " - Page " + page + " ===", NamedTextColor.GOLD));

        for (int i = startIndex; i < endIndex; i++) {
            PlayerStatsManager.LeaderboardEntry entry = entries.get(i);
            String value = StatsFormat.leaderboardValue(levelBoard, entry.value());
            player.sendMessage(
                Component.text("#" + (i + 1) + " ", NamedTextColor.YELLOW)
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(value, NamedTextColor.GOLD))
            );
        }

        player.sendMessage(Component.text("Page " + page + "/" + totalPages + " - " + getUsage(), NamedTextColor.GRAY));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            Stream<String> prefixes = Stream.concat(GameMode.rankedTokens().stream(), Stream.of("level"));
            prefixes = Stream.concat(prefixes, Stat.tokens().stream());
            return TabComplete.startsWith(Stream.concat(prefixes, leaderboardHeroes()), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("level")) {
            return TabComplete.startsWith(leaderboardHeroes(), args[1]);
        }
        if (args.length == 2 && Stat.fromToken(args[0]).isPresent()) {
            return TabComplete.startsWith(Stream.concat(GameMode.rankedTokens().stream(), Stream.of("all")), args[1]);
        }
        return Collections.emptyList();
    }

    // Admin-only heroes stay off the level boards — nobody earns levels on them legitimately.
    private static Stream<String> leaderboardHeroes() {
        return HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name));
    }
}
