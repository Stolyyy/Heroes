package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.Configs;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lobby LEVEL leaderboard — a separate physical board from the elo one ({@link LeaderboardDisplay}),
 * built on the same per-player-visibility pattern: one {@link TextDisplay} stack per VIEW, where
 * view 0 is the overall smash-level board (the default) and views 1..N are one per hero. A single
 * Interaction covers the board; right-click steps a player's view forward through the hero list,
 * left-click steps back (with this many heroes, one-directional cycling would be a chore — and a
 * better navigation than click-cycling is a known later item).
 *
 * <p>Config: {@code level-leaderboard:} in lobby.yml (enabled/entries/line-spacing/title/location);
 * {@code {view}} in the title substitutes "Overall" or the hero's display name. Entities are tagged
 * {@code heroes:level_leaderboard}; the orphan sweeps of the two boards each spare the other's tag,
 * so the boards can stand close together without eating each other on respawn.
 */
public class LevelLeaderboardDisplay {

    /** Package-visible so the elo board's untagged-purge can recognize (and spare) ours. */
    static final NamespacedKey ENTITY_TAG = new NamespacedKey("heroes", "level_leaderboard");

    /** View 0 = overall; 1..N = HeroConfig names (sorted). */
    private final List<String> views = new ArrayList<>();
    private final Map<Integer, List<TextDisplay>> displaysByView = new HashMap<>();
    private Interaction interaction;
    private final Map<UUID, Integer> playerView = new HashMap<>();

    private boolean enabled;
    private int entries;
    private double lineSpacing;
    private Location location;
    private String titleText;

    public LevelLeaderboardDisplay() {
        loadConfig();
    }

    private void loadConfig() {
        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");
        if (!file.exists()) {
            enabled = false;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("level-leaderboard");
        if (section == null) {
            enabled = false;
            return;
        }

        enabled = section.getBoolean("enabled", false);
        if (!enabled) return;

        entries = Configs.requireInt(section, "entries");
        lineSpacing = Configs.requireDouble(section, "line-spacing");
        titleText = Configs.requireString(section, "title");

        ConfigurationSection locSection = Configs.requireSection(section, "location");
        World world = Bukkit.getWorld(Configs.requireString(locSection, "world"));
        if (world == null) {
            // Not a config mistake — the configured world simply isn't loaded on this server.
            enabled = false;
            return;
        }
        location = new Location(world,
                Configs.requireDouble(locSection, "x"),
                Configs.requireDouble(locSection, "y"),
                Configs.requireDouble(locSection, "z"));
    }

    public void spawn() {
        if (!enabled || location == null) return;

        location.getChunk().setForceLoaded(true);
        location.getChunk().load();

        despawn();
        cleanupOrphans();

        // Views resolve at spawn time so a hero added by config reload appears after respawn.
        // Admin-only heroes are excluded — nobody earns levels on them legitimately.
        views.clear();
        views.add(null); // overall
        views.addAll(HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name))
                .sorted().toList());

        for (int v = 0; v < views.size(); v++) {
            List<TextDisplay> stack = new ArrayList<>();
            spawnDisplaySet(viewTitle(views.get(v)), stack);
            displaysByView.put(v, stack);
        }
        spawnInteraction();

        refresh();

        for (Player p : Bukkit.getOnlinePlayers()) {
            applyVisibility(p);
        }
    }

    private String viewTitle(String heroOrNull) {
        return titleText.replace("{view}", heroOrNull == null ? "Overall" : HeroConfig.formatName(heroOrNull));
    }

    private void cleanupOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : new ArrayList<>(world.getEntities())) {
                if (e.getPersistentDataContainer().has(ENTITY_TAG, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }

        for (Entity e : location.getWorld().getNearbyEntities(location, 0.5, 6, 0.5)) {
            // The elo board may stand nearby — spare its entities (it manages its own).
            if (LeaderboardDisplay.heroesOwned(e)) {
                continue;
            }
            if (e instanceof TextDisplay || e instanceof Interaction) {
                e.remove();
            }
        }
    }

    private void spawnDisplaySet(String title, List<TextDisplay> targetList) {
        World world = location.getWorld();

        TextDisplay titleDisplay = world.spawn(location, TextDisplay.class, d -> {
            d.text(MiniMessage.miniMessage().deserialize(title));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setPersistent(false);
            d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
        });
        targetList.add(titleDisplay);

        for (int i = 0; i < entries; i++) {
            Location lineLoc = location.clone().add(0, -(lineSpacing * (i + 1)), 0);
            TextDisplay line = world.spawn(lineLoc, TextDisplay.class, d -> {
                d.text(Component.empty());
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setPersistent(false);
                d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
            });
            targetList.add(line);
        }
    }

    private void spawnInteraction() {
        World world = location.getWorld();
        Location bottom = location.clone().subtract(0, lineSpacing * entries, 0);
        interaction = world.spawn(bottom, Interaction.class, i -> {
            i.setInteractionWidth(3.0f);
            i.setInteractionHeight((float) (lineSpacing * (entries + 1) + 0.5));
            i.setResponsive(true);
            i.setPersistent(false);
            i.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
        });
    }

    public void despawn() {
        for (List<TextDisplay> stack : displaysByView.values()) {
            for (TextDisplay d : stack) if (d.isValid()) d.remove();
        }
        if (interaction != null && interaction.isValid()) interaction.remove();
        displaysByView.clear();
        interaction = null;
    }

    /** Re-query levels for every view and rewrite the entity text. */
    public void refresh() {
        if (!enabled) return;
        boolean broken = displaysByView.isEmpty()
                || interaction == null || !interaction.isValid()
                || displaysByView.values().stream().flatMap(List::stream).anyMatch(d -> !d.isValid());
        if (broken) {
            spawn();
            return;
        }
        // Same as the elo board: rankings come from the in-memory board index, so refreshing every
        // view synchronously at game end costs nothing — no per-view disk walk to multiply.
        for (int v = 0; v < views.size(); v++) {
            refreshSet(views.get(v), displaysByView.get(v));
        }
    }

    private void refreshSet(String heroOrNull, List<TextDisplay> displays) {
        if (displays == null || displays.isEmpty()) return;
        List<PlayerStatsManager.LeaderboardEntry> top = PlayerStatsManager.getTopByLevel(heroOrNull, entries);

        for (int i = 0; i < entries; i++) {
            int displayIndex = i + 1;
            if (displayIndex >= displays.size()) break;
            TextDisplay d = displays.get(displayIndex);
            if (!d.isValid()) continue;

            if (i < top.size()) {
                PlayerStatsManager.LeaderboardEntry entry = top.get(i);
                d.text(Component.text((i + 1) + ". ", NamedTextColor.GOLD)
                        .append(Component.text(entry.name(), NamedTextColor.WHITE))
                        .append(Component.text(" - Lv. " + entry.value(), NamedTextColor.YELLOW)));
            } else {
                d.text(Component.text((i + 1) + ". ", NamedTextColor.GOLD)
                        .append(Component.text("---", NamedTextColor.GRAY)));
            }
        }
    }

    public boolean isInteractionEntity(Entity e) {
        return interaction != null && interaction.equals(e);
    }

    /** Step the player's view forward (right-click) or backward (left-click) through the cycle. */
    public void cyclePlayerView(Player player, boolean forward) {
        int current = playerView.getOrDefault(player.getUniqueId(), 0);
        int next = Math.floorMod(current + (forward ? 1 : -1), views.size());
        playerView.put(player.getUniqueId(), next);
        applyVisibility(player);
        String label = views.get(next) == null ? "Overall" : HeroConfig.formatName(views.get(next));
        player.sendActionBar(Component.text("Level Leaderboard: " + label, NamedTextColor.GOLD));
    }

    /** Put the player's view back on the overall board (view 0). */
    public void resetView(Player player) {
        playerView.put(player.getUniqueId(), 0);
        applyVisibility(player);
        player.sendActionBar(Component.text("Level Leaderboard: Overall", NamedTextColor.GOLD));
    }

    public void applyVisibility(Player player) {
        if (interaction == null) return; // not spawned yet
        int view = playerView.computeIfAbsent(player.getUniqueId(), id -> 0);
        Heroes plugin = Heroes.getInstance();
        for (Map.Entry<Integer, List<TextDisplay>> entry : displaysByView.entrySet()) {
            boolean shown = entry.getKey() == view;
            for (TextDisplay d : entry.getValue()) {
                if (shown) player.showEntity(plugin, d);
                else player.hideEntity(plugin, d);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
