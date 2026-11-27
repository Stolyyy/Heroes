package me.stolyy.heroes.discord;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.DiscordWebhook;
import me.stolyy.heroes.util.GameConfigManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Embedded JDA Discord bot serving read-only slash commands ({@code /stats}, {@code /leaderboard},
 * {@code /games}, {@code /online}) registered on a single guild.
 */
public final class DiscordBot {
    static final Color EMBED_COLOR = new Color(0xF5A623);

    private static final Map<String, BotCommand> COMMANDS = new LinkedHashMap<>();
    private static final Map<Long, Long> USER_COOLDOWNS = new ConcurrentHashMap<>();

    private static volatile JDA jda;
    private static volatile boolean shutdownRequested;

    static {
        register(new StatsSlashCommand());
        register(new LeaderboardSlashCommand());
        register(new GamesSlashCommand());
        register(new OnlineSlashCommand());
    }

    private DiscordBot() {}

    private static void register(BotCommand command) {
        COMMANDS.put(command.name(), command);
    }

    /** Connects JDA off the main thread when {@code discord.bot.token} is configured. */
    public static void start() {
        shutdownRequested = false;
        USER_COOLDOWNS.clear();

        String token = GameConfigManager.getDiscordBotToken().trim();
        if (token.isEmpty()) return;

        Logger logger = Heroes.getInstance().getLogger();
        String guildId = GameConfigManager.getDiscordBotGuildId().trim();
        if (guildId.isEmpty()) {
            logger.severe("Discord bot disabled: discord.bot.token is set but discord.bot.guild-id is empty.");
            return;
        }

        // Validate required bot config keys up front on the main thread.
        GameConfigManager.getDiscordBotCommandCooldownSeconds();
        GameConfigManager.getDiscordBotGamesLookbackMonths();
        GameConfigManager.getMessageString("discord.bot.unknown-player");
        GameConfigManager.getMessageString("discord.bot.wrong-channel");
        GameConfigManager.getMessageString("discord.bot.cooldown");

        Bukkit.getScheduler().runTaskAsynchronously(Heroes.getInstance(), () -> connect(token, guildId));
    }

    private static void connect(String token, String guildId) {
        Logger logger = Heroes.getInstance().getLogger();
        JDA built = null;
        // Intentional caught-and-logged failure: Discord being unreachable or misconfigured must
        // never stop the Minecraft server from enabling (never log the token value).
        try {
            built = JDABuilder.createLight(token, EnumSet.noneOf(GatewayIntent.class))
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .disableCache(EnumSet.allOf(CacheFlag.class))
                    .addEventListeners(new InteractionListener())
                    .build();
            built.awaitReady();

            if (shutdownRequested) {
                stopInstance(built);
                return;
            }

            Guild guild = built.getGuildById(guildId);
            if (guild == null) {
                logger.severe("Discord bot disabled: guild not found for discord.bot.guild-id '" + guildId + "'.");
                stopInstance(built);
                return;
            }

            jda = built;
            guild.updateCommands()
                    .addCommands(COMMANDS.values().stream().map(BotCommand::definition).toList())
                    .queue(
                            registered -> logger.info("Discord bot connected; registered " + registered.size()
                                    + " guild commands on " + guild.getName() + "."),
                            error -> logger.severe("Discord bot failed to register guild commands for discord.bot.guild-id '"
                                    + guildId + "': " + error.getMessage())
                    );
        } catch (InvalidTokenException e) {
            logger.severe("Discord bot disabled: invalid token configured in discord.bot.token.");
            if (built != null) stopInstance(built);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (built != null) stopInstance(built);
        } catch (Exception e) {
            logger.severe("Discord bot disabled: failed to connect (check discord.bot.token and discord.bot.guild-id): "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            if (built != null) stopInstance(built);
        }
    }

    /** Cleanly shuts down JDA on plugin disable so no background threads outlive the plugin. */
    public static void shutdown() {
        shutdownRequested = true;
        USER_COOLDOWNS.clear();
        JDA current = jda;
        jda = null;
        if (current != null) {
            stopInstance(current);
        }
    }

    private static void stopInstance(JDA instance) {
        instance.shutdown();
        try {
            if (!instance.awaitShutdown(Duration.ofSeconds(3))) {
                instance.shutdownNow();
            }
        } catch (InterruptedException e) {
            instance.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static void replyEmbed(InteractionHook hook, EmbedBuilder embed) {
        hook.editOriginalEmbeds(embed.build())
                .setAllowedMentions(List.of())
                .queue();
    }

    static void replyText(InteractionHook hook, String message) {
        hook.editOriginal(message)
                .setAllowedMentions(List.of())
                .queue();
    }

    static String unknownPlayerMessage(String name) {
        return GameConfigManager.getMessageString("discord.bot.unknown-player")
                .replace("{player}", DiscordWebhook.escapeMarkdown(name));
    }

    private static final class InteractionListener extends ListenerAdapter {
        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            BotCommand command = COMMANDS.get(event.getName());
            if (command == null) return;

            String allowedChannel = GameConfigManager.getDiscordBotChannelId().trim();
            if (!allowedChannel.isEmpty() && !event.getChannel().getId().equals(allowedChannel)) {
                String msg = GameConfigManager.getMessageString("discord.bot.wrong-channel")
                        .replace("{channel}", allowedChannel);
                event.reply(msg)
                        .setEphemeral(true)
                        .setAllowedMentions(List.of())
                        .queue();
                return;
            }

            long cooldownMs = GameConfigManager.getDiscordBotCommandCooldownSeconds() * 1000L;
            if (cooldownMs > 0) {
                long userId = event.getUser().getIdLong();
                long now = System.currentTimeMillis();
                Long last = USER_COOLDOWNS.get(userId);
                if (last != null && now - last < cooldownMs) {
                    event.reply(GameConfigManager.getMessageString("discord.bot.cooldown"))
                            .setEphemeral(true)
                            .setAllowedMentions(List.of())
                            .queue();
                    return;
                }
                USER_COOLDOWNS.put(userId, now);
            }

            command.handle(event);
        }

        @Override
        public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
            BotCommand command = COMMANDS.get(event.getName());
            if (command != null) {
                command.autocomplete(event);
            }
        }
    }
}
