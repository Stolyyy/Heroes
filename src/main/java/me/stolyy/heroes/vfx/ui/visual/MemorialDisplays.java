package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.SpecList;
import me.stolyy.heroes.vfx.effect.DisplayTags;
import me.stolyy.heroes.vfx.effect.TextDisplayBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Static lobby plaques from lobby.yml {@code memorials:}. Each entry is one multi-line {@link TextDisplay}. */
public class MemorialDisplays {
    private final List<TextDisplay> displays = new ArrayList<>();

    public void spawn() {
        despawn();
        DisplayTags.sweep(DisplayTags.MEMORIAL);

        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");
        if (!file.exists()) {
            Heroes.getInstance().getLogger().warning("lobby.yml not found; skipping memorial displays");
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> raw : config.getMapList("memorials")) {
            spawnOne(SpecList.fromMap(raw));
        }
    }

    private void spawnOne(ConfigurationSection entry) {
        String worldName = Configs.requireString(entry, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Heroes.getInstance().getLogger().warning("Skipping lobby.yml memorial: world '" + worldName + "' is not loaded");
            return;
        }
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
        displays.add(TextDisplayBuilder.create(location)
                .text(text)
                .billboard(Display.Billboard.CENTER)
                .transparentBackground()
                .tag(DisplayTags.MEMORIAL)
                .spawn());
    }

    public void despawn() {
        displays.forEach(Entity::remove);
        displays.clear();
    }
}
