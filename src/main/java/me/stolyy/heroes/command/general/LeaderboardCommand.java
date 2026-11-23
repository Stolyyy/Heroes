package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class LeaderboardCommand extends Command {
    private static final int ENTRIES_PER_PAGE = 10;
    private static final List<String> STAT_NAMES = List.of("kills", "deaths", "wins", "games");

    public LeaderboardCommand() {
        super("leaderboard");
        this.setDescription("View ELO, level and stat leaderboard rankings");
        this.setUsage("/leaderboard [1v1|2v2|level|kills|deaths|wins|games] [hero|1v1|2v2|all] [page]");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        GameMode mode = null;
        PlayerStatsManager.Stat stat = null;
        boolean levelBoard = false;
        String hero = null;
        int page = 1;

        for (String arg : args) {
            switch (arg.toLowerCase()) {
                case "1v1" -> mode = GameMode.ONE_V_ONE;
                case "2v2" -> mode = GameMode.TWO_V_TWO;
                case "all" -> mode = null;
                case "level", "levels" -> levelBoard = true;
                case "kills" -> stat = PlayerStatsManager.Stat.KILLS;
                case "deaths" -> stat = PlayerStatsManager.Stat.DEATHS;
                case "wins" -> stat = PlayerStatsManager.Stat.WINS;
                case "games" -> stat = PlayerStatsManager.Stat.GAMES;
                default -> {
                    if (HeroConfig.exists(arg.toLowerCase())) {
                        // A hero filter only makes sense on the level board — imply it, so
                        // "/lb karakot" works without the extra word.
                        hero = arg.toLowerCase();
                        levelBoard = true;
                        break;
                    }
                    try {
                        page = Integer.parseInt(arg);
                        if (page < 1) page = 1;
                    } catch (NumberFormatException e) {
                        player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.RED));
                        return true;
                    }
                }
            }
        }

        boolean statBoard = !levelBoard && stat != null;
        if (!levelBoard && !statBoard && mode == null) mode = GameMode.ONE_V_ONE;

        List<PlayerStatsManager.LeaderboardEntry> entries = levelBoard
                ? PlayerStatsManager.getTopByLevel(hero, page * ENTRIES_PER_PAGE)
                : statBoard
                    ? PlayerStatsManager.getTopByStat(stat, mode, page * ENTRIES_PER_PAGE)
                    : PlayerStatsManager.getTopPlayers(mode, page * ENTRIES_PER_PAGE);
        int totalEntries = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalEntries / ENTRIES_PER_PAGE));
        if (page > totalPages) page = totalPages;

        int startIndex = (page - 1) * ENTRIES_PER_PAGE;
        int endIndex = Math.min(startIndex + ENTRIES_PER_PAGE, totalEntries);

        String title = levelBoard
                ? "Level Leaderboard (" + (hero == null ? "Overall" : HeroConfig.formatName(hero)) + ")"
                : statBoard
                    ? stat.displayName() + " Leaderboard (" + (mode == null ? "All Modes" : mode.displayName()) + ")"
                    : "ELO Leaderboard (" + mode.displayName() + ")";
        player.sendMessage(Component.text("=== " + title + " - Page " + page + " ===", NamedTextColor.GOLD));

        for (int i = startIndex; i < endIndex; i++) {
            PlayerStatsManager.LeaderboardEntry entry = entries.get(i);
            String value = levelBoard ? "Lv. " + entry.value() : String.valueOf(entry.value());
            player.sendMessage(
                Component.text("#" + (i + 1) + " ", NamedTextColor.YELLOW)
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(value, NamedTextColor.GOLD))
            );
        }

        player.sendMessage(Component.text("Page " + page + "/" + totalPages + " - " + getUsage(), NamedTextColor.GRAY));

        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) {
            String typed = args[0].toLowerCase(Locale.ROOT);
            return Stream.concat(Stream.of("1v1", "2v2", "level", "kills", "deaths", "wins", "games"), leaderboardHeroes())
                    .filter(option -> option.startsWith(typed))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("level")) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return leaderboardHeroes()
                    .filter(name -> name.startsWith(typed))
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && STAT_NAMES.contains(args[0].toLowerCase(Locale.ROOT))) {
            String typed = args[1].toLowerCase(Locale.ROOT);
            return Stream.of("1v1", "2v2", "all")
                    .filter(option -> option.startsWith(typed))
                    .toList();
        }
        return Collections.emptyList();
    }

    /** Admin-only heroes stay off the level boards — nobody earns levels on them legitimately. */
    private static Stream<String> leaderboardHeroes() {
        return HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name));
    }
}
