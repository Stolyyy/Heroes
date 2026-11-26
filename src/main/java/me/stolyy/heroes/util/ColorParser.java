package me.stolyy.heroes.util;

import org.bukkit.Color;

import java.lang.reflect.Field;
import java.util.Locale;

/** Strict parser for YAML colors ({@code #RRGGBB}, {@code R,G,B}, or Bukkit {@link Color} constant name). */
public final class ColorParser {
    private ColorParser() {}

    public static Color parse(String str) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException("Color string cannot be null or blank");
        }
        String trimmed = str.trim();

        if (trimmed.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(trimmed.substring(1), 16);
                return Color.fromRGB(rgb);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid hex color '" + str + "' (expected #RRGGBB)", e);
            }
        }

        if (trimmed.contains(",")) {
            String[] parts = trimmed.split(",", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("RGB color must have 3 components, got '" + str + "'");
            }
            try {
                return Color.fromRGB(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())
                );
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid RGB color '" + str + "'", e);
            }
        }

        try {
            Field field = Color.class.getField(trimmed.toUpperCase(Locale.ROOT));
            if (field.getType() != Color.class) {
                throw new IllegalArgumentException("'" + str + "' is not a Bukkit Color constant");
            }
            return (Color) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("Unknown color '" + str
                    + "' (use a Bukkit Color name, #RRGGBB hex, or R,G,B)", e);
        }
    }
}
