package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.Configs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Static lobby plaques from lobby.yml {@code memorials:}: season placements, first-to-X achievements,
 * tournament winners, cosmetic hall labels. Each entry is one multi-line {@link TextDisplay}; the text is
 * MiniMessage. Season snapshots that write entries here are a separate feature; this only renders them.
 */
public class MemorialDisplays {
    private static final NamespacedKey ENTITY_TAG = new NamespacedKey(Heroes.getInstance(), "memorial");
    private final List<TextDisplay> displays = new ArrayList<>();

    public void spawn() {
        despawn();
        cleanupOrphans();

        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : config.getMapList("memorials")) {
            ConfigurationSection entry = config.createSection("memorials-parse", raw);
            spawnOne(entry);
        }
    }

    private void spawnOne(ConfigurationSection entry) {
        World world = Bukkit.getWorld(Configs.requireString(entry, "world"));
        if (world == null) return;
        Location location = new Location(world,
                Configs.requireDouble(entry, "x"),
                Configs.requireDouble(entry, "y"),
                Configs.requireDouble(entry, "z"));
        List<String> lines = entry.getStringList("lines");
        if (lines.isEmpty()) {
            throw new IllegalStateException("lobby.yml memorial at " + location.toVector() + " has no lines");
        }
        Component text = MiniMessage.miniMessage().deserialize(String.join("\n", lines));

        location.getChunk().setForceLoaded(true);
        location.getChunk().load();
        TextDisplay display = world.spawn(location, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setPersistent(false);
            d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
        });
        displays.add(display);
    }

    public void despawn() {
        displays.forEach(Entity::remove);
        displays.clear();
    }

    private void cleanupOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : world.getEntitiesByClass(TextDisplay.class)) {
                if (e.getPersistentDataContainer().has(ENTITY_TAG, PersistentDataType.BYTE)) e.remove();
            }
        }
    }
}
