package me.stolyy.heroes.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Shared tab-completion filters for command handlers. */
public final class TabComplete {

    private TabComplete() {}

    public static List<String> startsWith(Collection<String> options, String prefix) {
        return startsWith(options.stream(), prefix);
    }

    public static List<String> startsWith(Stream<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> onlinePlayers(String prefix) {
        return onlinePlayers(prefix, player -> true);
    }

    public static List<String> onlinePlayers(String prefix, Predicate<Player> filter) {
        return startsWith(
                Bukkit.getOnlinePlayers().stream()
                        .filter(filter)
                        .map(Player::getName),
                prefix);
    }
}
