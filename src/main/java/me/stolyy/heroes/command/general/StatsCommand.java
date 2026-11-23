package me.stolyy.heroes.command.general;

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
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /stats <player> [hero] [gamemode] [ability]} — progression rundowns at every depth of
 * the stats model (player → mode → hero → ability). Filter tokens after the player name are
 * classified by what they match (gamemode, then hero, then ability), so both
 * {@code /stats X karakot 1v1} and orders with the ability last work naturally. Ability names
 * with spaces are typed with underscores ({@code blazing_deagle}).
 */
public class StatsCommand extends Command {

    public StatsCommand() {
        super("stats");
        this.setDescription("Show a player's stats");
        this.setUsage("/stats <player> [hero] [gamemode] [ability]");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return true;
        }

        PlayerStats stats = PlayerStatsManager.lookupByName(args[0]);
        if (stats == null) {
            sender.sendMessage(Component.text("No stats found for " + args[0] + ".", NamedTextColor.RED));
            return true;
        }
        String displayName = stats.lastKnownName() != null ? stats.lastKnownName() : args[0];

        // Classify filter tokens: gamemode > hero > ability.
        GameMode mode = null;
        String hero = null;
        String ability = null;
        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (mode == null && parseMode(token) != null) {
                mode = parseMode(token);
            } else if (hero == null && HeroConfig.exists(token.toLowerCase())) {
                hero = token.toLowerCase();
            } else if (ability == null) {
                ability = token;
            } else {
                sender.sendMessage(Component.text("Unrecognized argument: " + token, NamedTextColor.RED));
                return true;
            }
        }

        if (ability != null && hero == null) {
            sender.sendMessage(Component.text("Specify the hero before the ability.", NamedTextColor.RED));
            return true;
        }

        if (ability != null) sendAbilityView(sender, stats, displayName, hero, mode, ability);
        else if (hero != null) sendHeroView(sender, stats, displayName, hero, mode);
        else if (mode != null) sendModeView(sender, stats, displayName, mode);
        else sendOverview(sender, stats, displayName);
        return true;
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

        // Most played = games (W+L) per hero across all modes.
        Map<String, Integer> games = new LinkedHashMap<>();
        for (ModeStats modeStats : stats.modeStats().values()) {
            for (Map.Entry<String, HeroProgress> e : modeStats.heroStats().entrySet()) {
                games.merge(e.getKey(), e.getValue().wins() + e.getValue().losses(), Integer::sum);
            }
        }
        String mostPlayed = games.entrySet().stream()
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

        // Aggregate the requested modes.
        List<GameMode> modes = mode != null ? List.of(mode) : List.of(GameMode.values());
        int wins = 0, losses = 0, deaths = 0, kills = 0;
        double damage = 0, voidDamage = 0;
        Map<String, AbilityStats> abilityTotals = new LinkedHashMap<>();
        for (GameMode m : modes) {
            HeroProgress progress = heroProgress(stats, m, hero);
            if (progress == null) continue;
            wins += progress.wins();
            losses += progress.losses();
            deaths += progress.deaths();
            kills += progress.totals().kills();
            damage += progress.totals().damage();
            voidDamage += progress.totals().voidDamage();
            for (Map.Entry<String, AbilityStats> e : progress.abilityStats().entrySet()) {
                AbilityStats sum = abilityTotals.computeIfAbsent(e.getKey(), k -> new AbilityStats());
                sum.setKills(sum.kills() + e.getValue().kills());
                sum.setDamage(sum.damage() + e.getValue().damage());
                sum.setVoidDamage(sum.voidDamage() + e.getValue().voidDamage());
            }
        }
        sender.sendMessage(recordLine(wins, losses, kills, deaths));
        sender.sendMessage(damageLine(damage, voidDamage));

        if (mode == null) {
            List<String> perMode = new ArrayList<>();
            for (GameMode m : GameMode.values()) {
                HeroProgress progress = heroProgress(stats, m, hero);
                if (progress == null || progress.wins() + progress.losses() == 0) continue;
                perMode.add(m.displayName() + " " + progress.wins() + "W-" + progress.losses() + "L");
            }
            sender.sendMessage(line("Per mode", perMode.isEmpty() ? "none yet" : String.join(" | ", perMode)));
        }

        String topAbilities = abilityTotals.entrySet().stream()
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
        List<GameMode> modes = mode != null ? List.of(mode) : List.of(GameMode.values());

        // Match the recorded ability keys (display names, e.g. "Blazing Deagle") tolerantly.
        String wanted = normalize(abilityToken);
        String matchedKey = null;
        int kills = 0;
        double damage = 0, voidDamage = 0;
        List<String> available = new ArrayList<>();
        for (GameMode m : modes) {
            HeroProgress progress = heroProgress(stats, m, hero);
            if (progress == null) continue;
            for (Map.Entry<String, AbilityStats> e : progress.abilityStats().entrySet()) {
                if (!available.contains(e.getKey())) available.add(e.getKey());
                if (normalize(e.getKey()).equals(wanted)) {
                    matchedKey = e.getKey();
                    kills += e.getValue().kills();
                    damage += e.getValue().damage();
                    voidDamage += e.getValue().voidDamage();
                }
            }
        }

        if (matchedKey == null) {
            sender.sendMessage(Component.text("No recorded stats for '" + abilityToken + "' on "
                    + HeroConfig.formatName(hero) + ". Recorded: "
                    + (available.isEmpty() ? "none" : String.join(", ", available)), NamedTextColor.RED));
            return;
        }

        header(sender, name + " — " + HeroConfig.formatName(hero) + " / " + matchedKey
                + (mode != null ? " (" + mode.displayName() + ")" : ""));
        sender.sendMessage(line("Kills", String.valueOf(kills)));
        sender.sendMessage(damageLine(damage, voidDamage));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Read-only view: null when the mode has no entry for this hero (avoids creating one). */
    private HeroProgress heroProgress(PlayerStats stats, GameMode mode, String hero) {
        ModeStats modeStats = stats.modeStats().get(mode);
        if (modeStats == null) return null;
        for (Map.Entry<String, HeroProgress> e : modeStats.heroStats().entrySet()) {
            if (e.getKey().equalsIgnoreCase(hero)) return e.getValue();
        }
        return null;
    }

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

    private static GameMode parseMode(String token) {
        return switch (token.toLowerCase()) {
            case "1v1", "one_v_one" -> GameMode.ONE_V_ONE;
            case "2v2", "two_v_two" -> GameMode.TWO_V_TWO;
            case "custom" -> GameMode.CUSTOM;
            default -> null;
        };
    }

    private static String normalize(String s) {
        return s.toLowerCase().replace('_', ' ').trim();
    }

    // -----------------------------------------------------------------------
    // Tab completion
    // -----------------------------------------------------------------------

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
        }

        // Which filters are already consumed by earlier args?
        boolean haveMode = false;
        String hero = null;
        for (int i = 1; i < args.length - 1; i++) {
            if (parseMode(args[i]) != null) haveMode = true;
            else if (hero == null && HeroConfig.exists(args[i].toLowerCase())) hero = args[i].toLowerCase();
        }

        List<String> options = new ArrayList<>();
        if (!haveMode) options.addAll(List.of("1v1", "2v2", "custom"));
        if (hero == null) options.addAll(HeroConfig.getAvailableNames());
        else options.addAll(abilityNames(hero));
        return filter(options, args[args.length - 1]);
    }

    /** Ability display names from the hero's yml (underscored for typability) plus Jab. */
    private static List<String> abilityNames(String hero) {
        List<String> names = new ArrayList<>();
        names.add("Jab");
        var config = HeroConfig.getConfig(hero);
        if (config == null) return names;
        for (String slot : List.of("primary", "secondary", "smash")) {
            String n = config.getString("abilities." + slot + ".name");
            if (n != null && !n.isEmpty()) names.add(n.replace(' ', '_'));
        }
        for (Map<?, ?> passive : config.getMapList("abilities.passives")) {
            Object n = passive.get("name");
            if (n != null && !n.toString().isEmpty()) names.add(n.toString().replace(' ', '_'));
        }
        return names;
    }

    private static List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .distinct()
                .sorted()
                .toList();
    }
}
