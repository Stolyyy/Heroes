package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.npc.NpcManager;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.DisplayTags;
import me.stolyy.heroes.vfx.effect.TextDisplayBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.HeroItems;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-viewer level/unlock tags mounted above lobby hero NPCs, refreshed whenever
 * {@link LobbyScoreboard#apply(Player)} runs.
 */
public final class HeroNpcLevelTags {

    private static final Map<UUID, List<TextDisplay>> byViewer = new HashMap<>();
    private static boolean initialized;

    private HeroNpcLevelTags() {}

    public static void initialize() {
        if (initialized) return;
        Bukkit.getPluginManager().registerEvents(new QuitListener(), Heroes.getInstance());
        initialized = true;
    }

    public static void refresh(Player viewer) {
        clear(viewer);

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
            Component tagText = buildTagText(viewer, heroName, stats, cap, lockedText);
            TextDisplay tag = TextDisplayBuilder.create(npc.getLocation())
                    .text(tagText)
                    .translation(0, (float) (tagHeight - npc.getHeight()), 0)
                    .billboard(Display.Billboard.CENTER)
                    .transparentBackground()
                    .tag(DisplayTags.HERO_LEVEL_TAG)
                    .spawn();
            npc.addPassenger(tag);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(viewer.getUniqueId())) other.hideEntity(plugin, tag);
            }
            viewer.showEntity(plugin, tag);
            tags.add(tag);
        }
        byViewer.put(viewer.getUniqueId(), tags);
    }

    private static Component buildTagText(Player viewer, String heroName, PlayerStats stats,
                                          int cap, Component lockedText) {
        if (HeroManager.isHeroUnlockedFor(viewer, heroName)) {
            return GuiText.component("visual.npc.level-tag",
                    "level", stats.getHeroLevel(heroName).level(),
                    "cap", cap);
        }
        Component text = lockedText;
        for (Component req : HeroItems.lockRequirementLore(heroName, "gui.hero.cost-npc")) {
            text = text.append(Component.newline()).append(req);
        }
        return text;
    }

    public static void clear(Player viewer) {
        List<TextDisplay> tags = byViewer.remove(viewer.getUniqueId());
        if (tags == null) return;
        for (TextDisplay tag : tags) {
            if (tag.isValid()) tag.remove();
        }
    }

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
