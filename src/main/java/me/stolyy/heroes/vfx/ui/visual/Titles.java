package me.stolyy.heroes.vfx.ui.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.time.Duration;

/** Shared {@link Title} timing presets and factories. */
public final class Titles {
    public static final Title.Times SHORT = times(250, 1000, 250);
    public static final Title.Times END = times(500, 3000, 500);

    private Titles() {}

    public static Title.Times times(long fadeInMs, long stayMs, long fadeOutMs) {
        return Title.Times.times(
                Duration.ofMillis(fadeInMs),
                Duration.ofMillis(stayMs),
                Duration.ofMillis(fadeOutMs));
    }

    public static Title of(Component title, Component subtitle, Title.Times times) {
        return Title.title(title, subtitle, times);
    }

    public static Title of(Component title, Component subtitle, Duration stay) {
        return Title.title(title, subtitle, Title.Times.times(
                Duration.ofMillis(250), stay, Duration.ofMillis(500)));
    }
}
