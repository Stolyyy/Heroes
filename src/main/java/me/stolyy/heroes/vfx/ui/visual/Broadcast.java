package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.physics.Interactions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Server-wide announcements to every real player, regardless of lobby or game. */
public final class Broadcast {
    private Broadcast() {}

    public static void chat(Component message) {
        for (Player player : realPlayers()) player.sendMessage(message);
    }

    public static void title(Component title, Component subtitle, Duration stay) {
        Title t = Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(250), stay, Duration.ofMillis(500)));
        for (Player player : realPlayers()) player.showTitle(t);
    }

    public static void chatAndTitle(Component message, Component title, Component subtitle, Duration stay) {
        chat(message);
        title(title, subtitle, stay);
    }

    private static Iterable<Player> realPlayers() {
        return Bukkit.getOnlinePlayers().stream().filter(Interactions::isRealPlayer).map(p -> (Player) p).toList();
    }
}
