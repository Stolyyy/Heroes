package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.npc.NpcManager;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-viewer "✫ x/20" tags under the lobby hero NPCs' nameplates — each player sees their OWN
 * level for that hero on the same NPC. One TextDisplay per (viewer, hero NPC), visible only to
 * its owner via per-player entity visibility — the same pattern the lobby leaderboards use.
 *
 * <p>Why not a BELOW_NAME scoreboard: several NPC names carry hex colours, which vanilla
 * nametags can't render through scoreboard teams — Citizens renders those nameplates itself, so
 * a vanilla below-name score never appears under them. Owning the text entity sidesteps
 * Citizens' name machinery entirely.
 *
 * <p>Refreshed from {@link LobbyScoreboard#apply} — the choke point every lobby entry, hero
 * select, and progression change already routes through.
 */
public final class HeroNpcLevelTags implements Listener {

    private HeroNpcLevelTags() {}

    /**
     * One line clear of the hero name. Citizens renders a coloured or >16-char NPC name as its own
     * TextDisplay hologram parked at exactly {@code bbHeight + 0.2} = 2.0 above the feet
     * (HologramTrait$TextDisplayRenderer.render0, name line offset 0/0/0), which is where this tag
     * used to sit - two TextDisplays at the identical point, printing over each other. 2.4 is one
     * Citizens hologram line (0.4) above that, and it also clears the plain vanilla nameplate that
     * short uncoloured names still use (anchored at bbHeight + 0.5 = 2.3, drawn downward from
     * there, so its glyphs stop around 2.33 while a TextDisplay anchored at 2.4 starts near 2.5).
     * The live value is game-config.yml {@code lobby.npc.tag-height}, measured from the NPC's feet.
     */

    /** Marks these tags as plugin-owned so the lobby leaderboards' orphan sweeps spare them. */
    private static final NamespacedKey ENTITY_TAG = new NamespacedKey("heroes", "hero_level_tag");

    // viewer uuid -> their personal tag displays
    private static final Map<UUID, List<TextDisplay>> byViewer = new HashMap<>();
    private static boolean initialized;

    /** (Re)builds the viewer's tags over every hero NPC and fixes visibility both directions. */
    public static void refresh(Player viewer) {
        if (!initialized) {
            Bukkit.getPluginManager().registerEvents(new QuitListener(), Heroes.getInstance());
            initialized = true;
        }
        clear(viewer);

        // Other viewers' tags must never render for this one (covers fresh joins).
        Heroes plugin = Heroes.getInstance();
        for (List<TextDisplay> others : byViewer.values()) {
            for (TextDisplay d : others) viewer.hideEntity(plugin, d);
        }

        FileConfiguration config = GameConfigManager.getGameConfig();
        double tagHeight = Configs.requireDouble(config, "lobby.npc.tag-height");
        Component lockedText = LegacyComponentSerializer.legacyAmpersand()
                .deserialize(Configs.requireString(config, "lobby.npc.locked-text"));
        PlayerStats stats = PlayerStatsManager.getStats(viewer);
        int cap = ProgressionConfig.getMasterSkinLevel();
        List<TextDisplay> tags = new ArrayList<>();
        for (Map.Entry<String, LivingEntity> entry : NpcManager.heroNpcEntities().entrySet()) {
            LivingEntity npc = entry.getValue();
            String heroName = entry.getKey();
            Component text;
            if (HeroManager.isHeroUnlockedFor(viewer, heroName)) {
                text = Component.text("✫ ", NamedTextColor.GOLD)
                        .append(Component.text(stats.getHeroLevel(heroName).level() + "/" + cap, NamedTextColor.WHITE));
            } else {
                int cost = HeroConfig.getUnlockCost(heroName);
                int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
                text = lockedText.append(Component.newline())
                        .append(Component.text("Cost: " + cost + (cost == 1 ? " token" : " tokens"), NamedTextColor.RED));
                if (levelReq > 0) {
                    text = text.append(Component.newline())
                            .append(Component.text("Requires Smash Level: " + levelReq, NamedTextColor.RED));
                }
            }
            Component tagText = text;
            TextDisplay tag = npc.getWorld().spawn(npc.getLocation(),
                    TextDisplay.class, d -> {
                        d.text(tagText);
                        // A passenger rides at the NPC's head; the translation lifts it to tagHeight.
                        Transformation base = d.getTransformation();
                        d.setTransformation(new Transformation(new Vector3f(0, (float) (tagHeight - npc.getHeight()), 0),
                                base.getLeftRotation(), base.getScale(), base.getRightRotation()));
                        d.setBillboard(Display.Billboard.CENTER);
                        d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        d.setPersistent(false);
                        d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
                    });
            npc.addPassenger(tag);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) other.hideEntity(plugin, tag);
            }
            viewer.showEntity(plugin, tag);
            tags.add(tag);
        }
        byViewer.put(viewer.getUniqueId(), tags);
    }

    public static void clear(Player viewer) {
        List<TextDisplay> tags = byViewer.remove(viewer.getUniqueId());
        if (tags == null) return;
        for (TextDisplay tag : tags) {
            if (tag.isValid()) tag.remove();
        }
    }

    /** Plugin-disable backstop. */
    public static void clearAll() {
        for (List<TextDisplay> tags : byViewer.values()) {
            for (TextDisplay tag : tags) {
                if (tag.isValid()) tag.remove();
            }
        }
        byViewer.clear();
    }

    public static class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            clear(event.getPlayer());
        }
    }
}
