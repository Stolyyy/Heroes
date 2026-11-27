package me.stolyy.heroes.discord;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.ModeStats;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.StatsFormat;
import me.stolyy.heroes.util.DiscordWebhook;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;

/** {@code /stats <player> [mode] [hero]} — mirrors in-game {@code StatsCommand} via {@link StatsFormat}. */
final class StatsSlashCommand implements BotCommand {

    @Override
    public String name() {
        return "stats";
    }

    @Override
    public SlashCommandData definition() {
        OptionData modeOption = new OptionData(OptionType.STRING, "mode", "Filter by game mode", false)
                .addChoice("1v1", "1v1")
                .addChoice("2v2", "2v2")
                .addChoice("Custom", "custom");
        OptionData heroOption = new OptionData(OptionType.STRING, "hero", "Filter by hero", false)
                .setAutoComplete(true);
        return Commands.slash(name(), "Show a player's stats")
                .addOption(OptionType.STRING, "player", "Minecraft username", true)
                .addOptions(modeOption, heroOption);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String playerArg = event.getOption("player", "", OptionMapping::getAsString).trim();
        String modeArg = event.getOption("mode", null, OptionMapping::getAsString);
        String heroArg = event.getOption("hero", null, OptionMapping::getAsString);

        event.deferReply().queue();
        InteractionHook hook = event.getHook();

        Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> {
            PlayerStats stats = PlayerStatsManager.lookupByName(playerArg);
            if (stats == null) {
                DiscordBot.replyText(hook, DiscordBot.unknownPlayerMessage(playerArg));
                return;
            }

            GameMode mode = modeArg != null ? GameMode.fromToken(modeArg).orElse(null) : null;
            String heroId = heroArg != null ? HeroConfig.resolve(heroArg.trim()) : null;
            if (heroId != null && (!HeroConfig.exists(heroId) || HeroConfig.isAdminOnly(heroId))) {
                DiscordBot.replyText(hook, "Unknown hero **" + DiscordWebhook.escapeMarkdown(heroArg) + "**.");
                return;
            }

            String displayName = DiscordWebhook.escapeMarkdown(StatsFormat.displayName(stats, playerArg));
            EmbedBuilder embed = new EmbedBuilder().setColor(DiscordBot.EMBED_COLOR);
            if (heroId != null) {
                populateHeroView(embed, stats, displayName, heroId, mode);
            } else if (mode != null) {
                populateModeView(embed, stats, displayName, mode);
            } else {
                populateOverview(embed, stats, displayName);
            }
            DiscordBot.replyEmbed(hook, embed);
        });
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        if ("hero".equals(event.getFocusedOption().getName())) {
            BotCommand.autocompleteHeroes(event);
        }
    }

    private static void populateOverview(EmbedBuilder embed, PlayerStats stats, String displayName) {
        embed.setTitle(displayName + " — Stats Overview")
                .addField("Smash Level", stats.smashLevel() + " ✫", true)
                .addField("Tokens", String.valueOf(stats.getTokenBalance()), true)
                .addField("Elo", "1v1 " + stats.getElo(GameMode.ONE_V_ONE)
                        + " | 2v2 " + stats.getElo(GameMode.TWO_V_TWO), false)
                .addField("Record", StatsFormat.record(stats.totalWins(), stats.totalLosses(),
                        stats.totalKills(), stats.totalDeaths()), false)
                .addField("Damage", StatsFormat.damage(stats.totalDamage(), stats.totalVoidDamage()), false)
                .addField("Most played", StatsFormat.mostPlayed(stats, 3), false);
    }

    private static void populateModeView(EmbedBuilder embed, PlayerStats stats, String displayName, GameMode mode) {
        ModeStats modeStats = stats.getModeStats(mode);
        embed.setTitle(displayName + " — " + mode.displayName());
        if (mode.hasElo()) {
            embed.addField("Elo", String.valueOf(stats.getElo(mode)), true);
        }
        embed.addField("Record", StatsFormat.record(modeStats.totalWins(), modeStats.totalLosses(),
                        modeStats.totalKills(), modeStats.totalDeaths()), false)
                .addField("Damage", StatsFormat.damage(modeStats.totalDamage(), modeStats.totalVoidDamage()), false)
                .addField("Heroes", StatsFormat.modeHeroes(modeStats), false);
    }

    private static void populateHeroView(EmbedBuilder embed, PlayerStats stats, String displayName,
                                         String heroId, GameMode mode) {
        String heroDisplay = HeroConfig.formatName(heroId);
        embed.setTitle(displayName + " — " + heroDisplay + (mode != null ? " (" + mode.displayName() + ")" : ""));

        HeroLevel level = stats.getHeroLevel(heroId);
        PlayerStats.HeroSummary summary = stats.summarizeHero(heroId, mode);
        embed.addField("Level", StatsFormat.heroLevel(level), false)
                .addField("Record", StatsFormat.record(summary.wins(), summary.losses(),
                        summary.kills(), summary.deaths()), false)
                .addField("Damage", StatsFormat.damage(summary.damage(), summary.voidDamage()), false);
        if (mode == null) {
            embed.addField("Per mode", StatsFormat.heroPerMode(stats, heroId), false);
        }
        embed.addField("Top abilities", StatsFormat.topAbilities(summary, 4), false);
    }
}
