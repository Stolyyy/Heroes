package me.stolyy.heroes.progression;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Kinds of Skin Shards stored per player. {@code HERO_SELECT} and {@code SKIN_SELECT} are reserved for part 2. */
public enum ShardKind {
    RANDOM("random", "Random"),
    HERO_SELECT("hero", "Hero Select"),
    SKIN_SELECT("skin", "Skin Select");

    private final String commandToken;
    private final String displayName;

    ShardKind(String commandToken, String displayName) {
        this.commandToken = commandToken;
        this.displayName = displayName;
    }

    public String commandToken() { return commandToken; }
    public String displayName() { return displayName; }
    public String storageKey() { return name().toLowerCase(Locale.ROOT); }

    public static @Nullable ShardKind fromCommandToken(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "random" -> RANDOM;
            case "hero", "hero_select" -> HERO_SELECT;
            case "skin", "skin_select" -> SKIN_SELECT;
            default -> null;
        };
    }
}
