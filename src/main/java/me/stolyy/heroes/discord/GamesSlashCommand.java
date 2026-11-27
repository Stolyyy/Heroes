package me.stolyy.heroes.discord;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.MatchLog;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.StatsFormat;
import me.stolyy.heroes.util.DiscordWebhook;
import me.stolyy.heroes.util.GameConfigManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;

/** {@code /games <player>} — lists the player's last 10 finished matches from monthly JSONL match logs. */
final class GamesSlashCommand implements BotCommand {
    private static final int MAX_GAMES = 10;

    @Override
    public String name() {
        return "games";
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash(name(), "Show a player's last 10 finished games")
                .addOption(OptionType.STRING, "player", "Minecraft username", true);
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        String playerArg = event.getOption("player", "", OptionMapping::getAsString).trim();

        event.deferReply().queue();
        InteractionHook hook = event.getHook();

        // Step 1: resolve username -> UUID on the main thread via PlayerStatsManager.
        Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> {
            PlayerStats stats = PlayerStatsManager.lookupByName(playerArg);
            if (stats == null) {
                DiscordBot.replyText(hook, DiscordBot.unknownPlayerMessage(playerArg));
                return;
            }
            UUID playerId = stats.playerId();
            String displayName = StatsFormat.displayName(stats, playerArg);
            int lookbackMonths = GameConfigManager.getDiscordBotGamesLookbackMonths();

            // Step 2: scan monthly match-log files asynchronously.
            Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () -> {
                List<MatchLog.PlayerGameRecord> games = MatchLog.recentGamesForPlayer(playerId, MAX_GAMES, lookbackMonths);
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(DiscordBot.EMBED_COLOR)
                        .setTitle(DiscordWebhook.escapeMarkdown(displayName) + " — Recent Games");

                if (games.isEmpty()) {
                    embed.setDescription("No finished games found in the last " + lookbackMonths
                            + (lookbackMonths == 1 ? " month." : " months."));
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < games.size(); i++) {
                        MatchLog.PlayerGameRecord g = games.get(i);
                        if (i > 0) sb.append('\n');
                        String outcome = switch (g.outcome()) {
                            case WIN -> "**Win**";
                            case LOSS -> "**Loss**";
                            case DRAW -> "**Draw**";
                        };
                        String heroDisplay = HeroConfig.exists(g.hero()) ? HeroConfig.formatName(g.hero()) : g.hero();
                        long epochSeconds = g.endedAt() / 1000L;
                        sb.append(outcome)
                                .append(" • ").append(DiscordWebhook.escapeMarkdown(g.mode()))
                                .append(" • ").append(DiscordWebhook.escapeMarkdown(g.map()))
                                .append(" • ").append(DiscordWebhook.escapeMarkdown(heroDisplay))
                                .append(" • `").append(g.kills()).append('/').append(g.deaths()).append("` K/D")
                                .append(" • <t:").append(epochSeconds).append(":R>");
                    }
                    embed.setDescription(sb.toString());
                }
                DiscordBot.replyEmbed(hook, embed);
            });
        });
    }
}
