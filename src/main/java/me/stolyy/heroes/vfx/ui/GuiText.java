package me.stolyy.heroes.vfx.ui;

import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Throwing MiniMessage reader over {@code messages.yml} for presentation keys
 * ({@code gui.*}, {@code prompt.*}, {@code scoreboard.*}, {@code visual.*}).
 */
public final class GuiText {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private GuiText() {}

    private static FileConfiguration cfg() {
        FileConfiguration config = GameConfigManager.getMessagesConfig();
        if (config == null) {
            throw new IllegalStateException("messages.yml has not been loaded yet");
        }
        return config;
    }

    public static String raw(String key) {
        return Configs.requireString(cfg(), key);
    }

    public static String raw(String key, Map<String, String> vars) {
        String text = Configs.requireString(cfg(), key);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return text;
    }

    public static String raw(String key, String k1, Object v1) {
        return raw(key, Map.of(k1, String.valueOf(v1)));
    }

    public static String raw(String key, String k1, Object v1, String k2, Object v2) {
        return raw(key, Map.of(k1, String.valueOf(v1), k2, String.valueOf(v2)));
    }

    public static Component component(String key) {
        return component(key, Map.of());
    }

    public static Component component(String key, Map<String, String> vars) {
        return parseComponent(applyEscaped(Configs.requireString(cfg(), key), vars));
    }

    public static Component component(String key, String k1, Object v1) {
        return component(key, Map.of(k1, String.valueOf(v1)));
    }

    public static Component component(String key, String k1, Object v1, String k2, Object v2) {
        return component(key, Map.of(k1, String.valueOf(v1), k2, String.valueOf(v2)));
    }

    public static List<Component> lore(String key) {
        return lore(key, Map.of());
    }

    public static List<Component> lore(String key, Map<String, String> vars) {
        FileConfiguration config = cfg();
        if (!config.contains(key)) {
            throw new IllegalStateException("Missing required message list '" + key + "' in messages.yml");
        }
        List<String> rawLines = config.getStringList(key);
        List<Component> lines = new ArrayList<>(rawLines.size());
        for (String line : rawLines) {
            lines.add(parseComponent(applyEscaped(line, vars)));
        }
        return lines;
    }

    public static List<Component> lore(String key, String k1, Object v1) {
        return lore(key, Map.of(k1, String.valueOf(v1)));
    }

    public static List<Component> lore(String key, String k1, Object v1, String k2, Object v2) {
        return lore(key, Map.of(k1, String.valueOf(v1), k2, String.valueOf(v2)));
    }

    private static String applyEscaped(String template, Map<String, String> vars) {
        String text = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            text = text.replace("{" + entry.getKey() + "}", MM.escapeTags(entry.getValue()));
        }
        return text;
    }

    private static Component parseComponent(String text) {
        if (text.isEmpty()) return Component.empty();
        return MM.deserialize(text).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
