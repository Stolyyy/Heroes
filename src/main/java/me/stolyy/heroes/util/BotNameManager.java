package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class BotNameManager {

    private static List<String> names = new ArrayList<>();
    private static final Random random = new Random();

    public static void load() {
        InputStream stream = Heroes.getInstance().getResource("bot-names.yml");
        if (stream == null) {
            Heroes.getInstance().getLogger().warning("bot-names.yml not found!");
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream));
        names = config.getStringList("names");
    }

    /**
     * Returns a random bot name not already in usedNames.
     * If all names are used, generates a fallback name like "Bot_7".
     *
     * Names are sanitized HERE — plain text, hard-capped at 16 chars — because the returned
     * string becomes the NPC's entity name (GameBotNpc.spawn), and a colored or >16-char
     * Citizens player-NPC name spawns a TextDisplay hologram over the vanilla nametag.
     * Sanitizing at the source keeps the one string identical everywhere it renders:
     * nameplate, scoreboard teams, tab, kill feed and the game GUIs. Dedup runs on the
     * sanitized form so two long names that truncate identically can't collide.
     */
    public static String getRandomName(Set<String> usedNames) {
        List<String> available = new ArrayList<>();
        for (String name : names) {
            String sanitized = sanitize(name);
            if (!sanitized.isEmpty() && !usedNames.contains(sanitized)) {
                available.add(sanitized);
            }
        }
        if (!available.isEmpty()) {
            return available.get(random.nextInt(available.size()));
        }
        // Fallback: generate a unique numbered name
        int n = 1;
        String fallback;
        do {
            fallback = "Bot_" + n++;
        } while (usedNames.contains(fallback));
        return fallback;
    }

    private static String sanitize(String name) {
        String plain = name.trim();
        return plain.length() > 16 ? plain.substring(0, 16) : plain;
    }
}
