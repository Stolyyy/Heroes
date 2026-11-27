package me.stolyy.heroes.discord;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.DiscordWebhook;
import me.stolyy.heroes.vfx.ui.visual.DiscordStatus;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;

import java.util.stream.Collectors;

/** {@code /online} — live player, queue, and match snapshot reusing {@link DiscordStatus#captureSnapshot()}. */
final class OnlineSlashCommand implements BotCommand {

    @Override
    public String name() {
        return "online";
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash(name(), "Show who is online and active queues/matches");
    }

    @Override
    public void handle(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        InteractionHook hook = event.getHook();

        Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> {
            DiscordStatus.Snapshot snap = DiscordStatus.captureSnapshot();
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(DiscordBot.EMBED_COLOR)
                    .setTitle("Players online (" + snap.onlinePlayers().size() + ")");

            if (!snap.onlinePlayers().isEmpty()) {
                String list = snap.onlinePlayers().stream()
                        .map(DiscordWebhook::escapeMarkdown)
                        .collect(Collectors.joining(", "));
                embed.setDescription(list);
            } else {
                embed.setDescription("Nobody online.");
            }

            embed.addField("1v1", "in queue (" + snap.oneVOneQueued() + ") | in game (" + snap.oneVOneInGame() + ")", false)
                    .addField("2v2", "in queue (" + snap.twoVTwoQueued() + ") | in game (" + snap.twoVTwoInGame() + ")", false)
                    .addField("Custom", "in game (" + snap.customInGame() + ")", false);
            DiscordBot.replyEmbed(hook, embed);
        });
    }
}
