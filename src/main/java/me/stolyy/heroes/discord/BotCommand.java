package me.stolyy.heroes.discord;

import me.stolyy.heroes.hero.HeroConfig;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;
import java.util.Locale;

/** One Discord slash command registered on the configured guild. */
interface BotCommand {
    String name();

    SlashCommandData definition();

    void handle(SlashCommandInteractionEvent event);

    default void autocomplete(CommandAutoCompleteInteractionEvent event) {}

    /** Replies with up to 25 non-admin-only hero choices matching the user's partial input. */
    static void autocompleteHeroes(CommandAutoCompleteInteractionEvent event) {
        String query = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        List<Command.Choice> choices = HeroConfig.getAvailableNames().stream()
                .filter(id -> !HeroConfig.isAdminOnly(id))
                .sorted()
                .filter(id -> id.contains(query)
                        || HeroConfig.formatName(id).toLowerCase(Locale.ROOT).contains(query))
                .limit(25)
                .map(id -> new Command.Choice(HeroConfig.formatName(id), id))
                .toList();
        event.replyChoices(choices).queue();
    }
}
