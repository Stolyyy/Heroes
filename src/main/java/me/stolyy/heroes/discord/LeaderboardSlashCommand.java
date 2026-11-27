package me.stolyy.heroes.discord;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStatsManager.LeaderboardEntry;
import me.stolyy.heroes.progression.PlayerStatsManager.Stat;
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

import java.util.List;
import java.util.Locale;

/** {@code /leaderboard <type> [mode] [hero]} — top 10 rankings sharing query/formatting with {@code LeaderboardCommand}. */
final class LeaderboardSlashCommand implements BotCommand {
    private static final int TOP_LIMIT = 10;

    @Override
    public String name() {
        return "leaderboard";
    }

    @Override
    public SlashCommandData definition() {
        OptionData typeOption = new OptionData(OptionType.STRING, "type", "Ranking category", true)
                .addChoice("Elo", "elo")
                .addChoice("Level", "level");
        for (String token : Stat.tokens()) {
            Stat stat = Stat.fromToken(token).orElseThrow();
            typeOption.addChoice(stat.displayName(), token);
        }

        OptionData modeOption = new OptionData(OptionType.STRING, "mode", "Ranked mode filter", false)
                .addChoice("1v1", "1v1")
                .addChoice("2v2", "2v2");
        OptionData heroOption = new OptionData(OptionType.STRING, "hero", "Hero filter (for level leaderboard)", false)
                .setAutoComplete(true);

        return Commands.slash(name(), "Show top 10 leaderboard rankings")
                .addOptions(typeOption, modeOption, heroOption);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String typeArg = event.getOption("type", "elo", OptionMapping::getAsString).toLowerCase(Locale.ROOT);
        String modeArg = event.getOption("mode", null, OptionMapping::getAsString);
        String heroArg = event.getOption("hero", null, OptionMapping::getAsString);

        event.deferReply().queue();
        InteractionHook hook = event.getHook();

        Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> {
            boolean levelBoard = "level".equals(typeArg);
            Stat stat = levelBoard || "elo".equals(typeArg) ? null : Stat.fromToken(typeArg).orElse(null);
            GameMode mode = modeArg != null ? GameMode.fromToken(modeArg).filter(GameMode::hasElo).orElse(null) : null;
            if (!levelBoard && stat == null && mode == null) {
                mode = GameMode.ONE_V_ONE;
            }

            String heroId = null;
            if (levelBoard && heroArg != null && !heroArg.isBlank()) {
                heroId = HeroConfig.resolve(heroArg.trim());
                if (!HeroConfig.exists(heroId) || HeroConfig.isAdminOnly(heroId)) {
                    DiscordBot.replyText(hook, "Unknown hero **" + DiscordWebhook.escapeMarkdown(heroArg) + "**.");
                    return;
                }
            }

            List<LeaderboardEntry> entries = StatsFormat.queryLeaderboard(levelBoard, heroId, stat, mode, TOP_LIMIT);
            String title = StatsFormat.leaderboardTitle(levelBoard, heroId, stat, mode);

            StringBuilder body = new StringBuilder();
            if (entries.isEmpty()) {
                body.append("No entries yet.");
            } else {
                for (int i = 0; i < entries.size(); i++) {
                    LeaderboardEntry entry = entries.get(i);
                    if (i > 0) body.append('\n');
                    body.append("**#").append(i + 1).append("** ")
                            .append(DiscordWebhook.escapeMarkdown(entry.name()))
                            .append(" — ")
                            .append(StatsFormat.leaderboardValue(levelBoard, entry.value()));
                }
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(DiscordBot.EMBED_COLOR)
                    .setTitle(title)
                    .setDescription(body.toString());
            DiscordBot.replyEmbed(hook, embed);
        });
    }

    @Override
    public void autocomplete(CommandAutoCompleteInteractionEvent event) {
        if ("hero".equals(event.getFocusedOption().getName())) {
            BotCommand.autocompleteHeroes(event);
        }
    }
}
