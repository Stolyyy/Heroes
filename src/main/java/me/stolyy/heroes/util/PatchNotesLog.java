package me.stolyy.heroes.util;

import me.stolyy.heroes.Heroes;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Appends live {@code /balance} mutations to {@code plugins/Heroes/patch-notes.yml} grouped under
 * {@code YYYY-MM-DD} headers, collapsing repeated same-day edits on the same key and dropping
 * reverted net-zero changes.
 */
public final class PatchNotesLog {
    private static final String ARROW = " -> ";

    private PatchNotesLog() {}

    private static File file() {
        return new File(Heroes.getInstance().getDataFolder(), "patch-notes.yml");
    }

    /** Records an applied {@code /balance} change for today's date. */
    public static void record(String target, String path, String oldValue, String newValue) {
        if (oldValue.equalsIgnoreCase(newValue)) return;

        String today = LocalDate.now().toString();
        File f = file();
        YamlConfiguration yaml = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        List<String> entries = new ArrayList<>(yaml.getStringList(today));

        String prefix = target + " " + path + " ";
        int existingIndex = -1;
        for (int i = 0; i < entries.size(); i++) {
            String line = entries.get(i);
            if (line.startsWith(prefix) && line.indexOf(ARROW, prefix.length()) >= 0) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex >= 0) {
            String existing = entries.get(existingIndex);
            int arrow = existing.indexOf(ARROW, prefix.length());
            String originalOld = existing.substring(prefix.length(), arrow);
            if (originalOld.equalsIgnoreCase(newValue)) {
                entries.remove(existingIndex);
            } else {
                entries.set(existingIndex, prefix + originalOld + ARROW + newValue);
            }
        } else {
            entries.add(prefix + oldValue + ARROW + newValue);
        }

        yaml.set(today, entries.isEmpty() ? null : entries);
        try {
            yaml.save(f);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save patch-notes.yml", e);
        }
    }
}
