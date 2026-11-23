package me.stolyy.heroes.game;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

import java.util.Arrays;

public enum TeamColor {
    RED(NamedTextColor.RED, "Red", Material.RED_WOOL),
    BLUE(NamedTextColor.BLUE, "Blue", Material.BLUE_WOOL),
    GREEN(NamedTextColor.GREEN, "Green", Material.GREEN_WOOL),
    YELLOW(NamedTextColor.YELLOW, "Yellow", Material.YELLOW_WOOL),
    PURPLE(NamedTextColor.DARK_PURPLE, "Purple", Material.PURPLE_WOOL),
    ORANGE(NamedTextColor.GOLD, "Orange", Material.ORANGE_WOOL),
    CYAN(NamedTextColor.AQUA, "Cyan", Material.CYAN_WOOL),
    PINK(NamedTextColor.LIGHT_PURPLE, "Pink", Material.PINK_WOOL),
    SPECTATOR(NamedTextColor.GRAY, "Spectator", Material.GRAY_WOOL);

    private final NamedTextColor textColor;
    private final String displayName;
    private final Material wool;

    TeamColor(NamedTextColor textColor, String displayName, Material wool) {
        this.textColor = textColor;
        this.displayName = displayName;
        this.wool = wool;
    }

    public NamedTextColor textColor() { return textColor; }
    public NamedTextColor chatColor() { return textColor; }
    public String displayName() { return displayName; }
    public Material wool() { return wool; }

    public static TeamColor fromIndex(int index) {
        TeamColor[] playable = Arrays.stream(values())
            .filter(c -> c != SPECTATOR)
            .toArray(TeamColor[]::new);
        return playable[index % playable.length];
    }
}
