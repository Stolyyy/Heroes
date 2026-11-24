package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.AbilityStats;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.HeroProgress;
import me.stolyy.heroes.progression.ModeStats;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /stats <player> [hero] [gamemode] [ability]} — progression rundowns at every depth of
 * the stats model (player → mode → hero → ability). Filter tokens after the player name are
 * classified by what they match (gamemode, then hero, then ability), so both
 * {@code /stats X karakot 1v1} and orders with the ability last work naturally. Ability names
 * with spaces are typed with underscores ({@code blazing_deagle}).
 */
public class StatsCommand extends HeroesCommand {

    private record Filters(GameMode mode, String hero, String ability, String unrecognized) {}

    public StatsCommand() {
        super("stats", "Show a player's stats",
                "/stats <player> [hero] [gamemode] [ability]",
                List.of(), null, false);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        PlayerStats stats = PlayerStatsManager.lookupByName(args[0]);
        if (stats == null) {
            sender.sendMessage(Component.text("No stats found for " + args[0] + ".", NamedTextColor.RED));
            return;
        }
        String displayName = stats.lastKnownName() != null ? stats.lastKnownName() : args[0];

        Filters filters = parseFilters(args, args.length);
        if (filters.unrecognized() != null) {
            sender.sendMessage(Component.text("Unrecognized argument: " + filters.unrecognized(), NamedTextColor.RED));
            return;
        }
        if (filters.ability() != null && filters.hero() == null) {
            sender.sendMessage(Component.text("Specify the hero before the ability.", NamedTextColor.RED));
            return;
        }

        if (filters.ability() != null) sendAbilityView(sender, stats, displayName, filters.hero(), filters.mode(), filters.ability());
        else if (filters.hero() != null) sendHeroView(sender, stats, displayName, filters.hero(), filters.mode());
        else if (filters.mode() != null) sendModeView(sender, stats, displayName, filters.mode());
        else sendOverview(sender, stats, displayName);
    }

    private static Filters parseFilters(String[] args, int endExclusive) {
        GameMode mode = null;
        String hero = null;
        String ability = null;
        for (int i = 1; i < endExclusive; i++) {
            String token = args[i];
            Optional<GameMode> parsedMode = GameMode.fromToken(token);
            if (mode == null && parsedMode.isPresent()) {
                mode = parsedMode.get();
            } else if (hero == null && HeroConfig.exists(token.toLowerCase(Locale.ROOT))) {
                hero = token.toLowerCase(Locale.ROOT);
            } else if (ability == null) {
                ability = token;
            } else {
                return new Filters(mode, hero, ability, token);
            }
        }
        return new Filters(mode, hero, ability, null);
    }

    // -----------------------------------------------------------------------
    // Views
    // -----------------------------------------------------------------------

    private void sendOverview(CommandSender sender, PlayerStats stats, String name) {
        header(sender, name);
        sender.sendMessage(line("Smash Level", stats.smashLevel() + " ✫")
                .append(Component.text("   Tokens: ", NamedTextColor.GRAY))
                .append(Component.text(stats.getTokenBalance(), NamedTextColor.WHITE)));
        sender.sendMessage(line("Elo", "1v1 " + stats.getElo(GameMode.ONE_V_ONE)
                + " | 2v2 " + stats.getElo(GameMode.TWO_V_TWO)));
        sender.sendMessage(recordLine(stats.totalWins(), stats.totalLosses(),
                stats.totalKills(), stats.totalDeaths()));
        sender.sendMessage(damageLine(stats.totalDamage(), stats.totalVoidDamage()));

        String mostPlayed = stats.gamesPerHero().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> HeroConfig.formatName(e.getKey()) + " (" + e.getValue() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("none yet");
        sender.sendMessage(line("Most played", mostPlayed));
        sender.sendMessage(Component.text("More: /stats " + name + " <hero|1v1|2v2|custom>", NamedTextColor.DARK_GRAY));
    }

    private void sendModeView(CommandSender sender, PlayerStats stats, String name, GameMode mode) {
        header(sender, name + " — " + mode.displayName());
        ModeStats modeStats = stats.getModeStats(mode);
        if (mode.hasElo()) sender.sendMessage(line("Elo", String.valueOf(stats.getElo(mode))));
        sender.sendMessage(recordLine(modeStats.totalWins(), modeStats.totalLosses(),
                modeStats.totalKills(), modeStats.totalDeaths()));
        sender.sendMessage(damageLine(modeStats.totalDamage(), modeStats.totalVoidDamage()));

        String perHero = modeStats.heroStats().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, HeroProgress> e)
                        -> e.getValue().wins() + e.getValue().losses()).reversed())
                .map(e -> HeroConfig.formatName(e.getKey()) + " "
                        + e.getValue().wins() + "W-" + e.getValue().losses() + "L")
                .reduce((a, b) -> a + " | " + b).orElse("none yet");
        sender.sendMessage(line("Heroes", perHero));
    }

    private void sendHeroView(CommandSender sender, PlayerStats stats, String name, String hero, GameMode mode) {
        String heroDisplay = HeroConfig.formatName(hero);
        header(sender, name + " — " + heroDisplay + (mode != null ? " (" + mode.displayName() + ")" : ""));

        HeroLevel level = stats.getHeroLevel(hero);
        sender.sendMessage(line("Level", level.level() + " (" + level.xp() + " xp into level)"));

        PlayerStats.HeroSummary summary = stats.summarizeHero(hero, mode);
        sender.sendMessage(recordLine(summary.wins(), summary.losses(), summary.kills(), summary.deaths()));
        sender.sendMessage(damageLine(summary.damage(), summary.voidDamage()));

        if (mode == null) {
            List<String> perMode = new ArrayList<>();
            for (GameMode m : GameMode.values()) {
                HeroProgress progress = stats.findHeroStats(m, hero);
                if (progress == null || progress.wins() + progress.losses() == 0) continue;
                perMode.add(m.displayName() + " " + progress.wins() + "W-" + progress.losses() + "L");
            }
            sender.sendMessage(line("Per mode", perMode.isEmpty() ? "none yet" : String.join(" | ", perMode)));
        }

        String topAbilities = summary.abilities().entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, AbilityStats> e)
                        -> e.getValue().damage()).reversed())
                .limit(4)
                .map(e -> e.getKey() + " " + fmt(e.getValue().damage()) + " dmg ("
                        + e.getValue().kills() + " kills)")
                .reduce((a, b) -> a + ", " + b).orElse("none yet");
        sender.sendMessage(line("Top abilities", topAbilities));
    }

    private void sendAbilityView(CommandSender sender, PlayerStats stats, String name,
                                 String hero, GameMode mode, String abilityToken) {
        PlayerStats.AbilityLookup result = stats.lookupAbility(hero, mode, abilityToken);
        if (result.matchedKey() == null) {
            sender.sendMessage(Component.text("No recorded stats for '" + abilityToken + "' on "
                    + HeroConfig.formatName(hero) + ". Recorded: "
                    + (result.available().isEmpty() ? "none" : String.join(", ", result.available())), NamedTextColor.RED));
            return;
        }

        header(sender, name + " — " + HeroConfig.formatName(hero) + " / " + result.matchedKey()
                + (mode != null ? " (" + mode.displayName() + ")" : ""));
        sender.sendMessage(line("Kills", String.valueOf(result.stats().kills())));
        sender.sendMessage(damageLine(result.stats().damage(), result.stats().voidDamage()));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void header(CommandSender sender, String title) {
        sender.sendMessage(Component.text("=== " + title + " ===", NamedTextColor.GOLD));
    }

    private Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    private Component recordLine(int wins, int losses, int kills, int deaths) {
        String kd = deaths > 0 ? String.format("%.2f", kills / (double) deaths) : "∞";
        return line("Record", wins + "W-" + losses + "L   K/D: " + kills + "/" + deaths + " (" + kd + ")");
    }

    private Component damageLine(double damage, double voidDamage) {
        return line("Damage", fmt(damage) + " (void " + fmt(voidDamage) + ")");
    }

    private String fmt(double d) {
        return String.valueOf(Math.round(d));
    }

    // -----------------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------------

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.onlinePlayers(args[0]);
        }

        Filters seen = parseFilters(args, args.length - 1);
        List<String> options = new ArrayList<>();
        if (seen.mode() == null) options.addAll(GameMode.tokens());
        if (seen.hero() == null) options.addAll(HeroConfig.getAvailableNames());
        else if (seen.ability() == null) options.addAll(HeroConfig.abilityDisplayNames(seen.hero()));
        return TabComplete.startsWith(options, args[args.length - 1]);
    }
}
