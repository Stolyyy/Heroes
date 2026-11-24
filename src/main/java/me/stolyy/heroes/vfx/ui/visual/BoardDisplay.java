package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.DisplayTags;
import me.stolyy.heroes.vfx.effect.TextDisplayBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared lobby leaderboard display backed by per-player entity visibility and parameterized by a
 * {@link Source}. Each view gets one {@link TextDisplay} stack overlaid at the same location with
 * an {@link Interaction} hitbox so clicking cycles views per viewer.
 */
public final class BoardDisplay<V> {

    public interface Source<V> {
        String configSection();

        List<V> views();

        String title(String template, V view);

        List<PlayerStatsManager.LeaderboardEntry> top(V view, int limit);

        Component value(PlayerStatsManager.LeaderboardEntry entry);

        Component viewLabel(V view);
    }

    private static final float CLICK_ZONE_WIDTH = 3.0f;
    private static final double CLICK_ZONE_EXTRA_HEIGHT = 0.5;
    private static final double UNTAGGED_PURGE_RADIUS_XZ = 0.5;
    private static final double UNTAGGED_PURGE_RADIUS_Y = 6.0;
    private static final List<BoardDisplay<?>> activeBoards = new ArrayList<>();

    private final Source<V> source;
    private final NamespacedKey entityTag;

    private List<V> views = List.of();
    private final List<List<TextDisplay>> displaysByView = new ArrayList<>();
    private Interaction interaction;
    private final Map<UUID, Integer> playerView = new HashMap<>();

    private boolean enabled;
    private int entries;
    private double lineSpacing;
    private Location location;
    private String titleText;

    public BoardDisplay(Source<V> source, NamespacedKey entityTag) {
        this.source = source;
        this.entityTag = entityTag;
        loadConfig();
        activeBoards.add(this);
        if (enabled) {
            Bukkit.getPluginManager().registerEvents(new BoardListener(), Heroes.getInstance());
        }
    }

    private void loadConfig() {
        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");
        if (!file.exists()) {
            enabled = false;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection(source.configSection());
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
        String worldName = Configs.requireString(locSection, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            Heroes.getInstance().getLogger().warning("Disabling lobby board '" + source.configSection()
                    + "': world '" + worldName + "' is not loaded");
            enabled = false;
            return;
        }
        location = new Location(world,
                Configs.requireDouble(locSection, "x"),
                Configs.requireDouble(locSection, "y"),
                Configs.requireDouble(locSection, "z"));
    }

    /** Spawns (or respawns) every view stack after sweeping lingering entities from prior runs. */
    public void spawn() {
        if (!enabled || location == null) return;

        // Keep non-persistent displays alive across empty-lobby chunk unloads.
        location.getChunk().setForceLoaded(true);
        location.getChunk().load();

        despawn();
        DisplayTags.sweep(entityTag);
        DisplayTags.sweepUntaggedNear(location, UNTAGGED_PURGE_RADIUS_XZ, UNTAGGED_PURGE_RADIUS_Y, UNTAGGED_PURGE_RADIUS_XZ);

        this.views = List.copyOf(source.views());
        if (views.isEmpty()) {
            throw new IllegalStateException("BoardDisplay source '" + source.configSection() + "' returned no views");
        }

        for (V view : views) {
            displaysByView.add(spawnDisplaySet(source.title(titleText, view)));
        }
        spawnInteraction();
        refreshContent();

        for (Player p : Bukkit.getOnlinePlayers()) {
            applyVisibility(p);
        }
    }

    private List<TextDisplay> spawnDisplaySet(String title) {
        List<TextDisplay> stack = new ArrayList<>(entries + 1);
        stack.add(TextDisplayBuilder.create(location)
                .text(MiniMessage.miniMessage().deserialize(title))
                .billboard(Display.Billboard.CENTER)
                .transparentBackground()
                .tag(entityTag)
                .spawn());

        for (int i = 0; i < entries; i++) {
            Location lineLoc = location.clone().add(0, -(lineSpacing * (i + 1)), 0);
            stack.add(TextDisplayBuilder.create(lineLoc)
                    .text(Component.empty())
                    .billboard(Display.Billboard.CENTER)
                    .transparentBackground()
                    .tag(entityTag)
                    .spawn());
        }
        return stack;
    }

    private void spawnInteraction() {
        World world = location.getWorld();
        Location bottom = location.clone().subtract(0, lineSpacing * entries, 0);
        interaction = world.spawn(bottom, Interaction.class, i -> {
            i.setInteractionWidth(CLICK_ZONE_WIDTH);
            i.setInteractionHeight((float) (lineSpacing * (entries + 1) + CLICK_ZONE_EXTRA_HEIGHT));
            i.setResponsive(true);
            i.setPersistent(false);
            DisplayTags.tag(i, entityTag);
        });
    }

    public void despawn() {
        for (List<TextDisplay> stack : displaysByView) {
            for (TextDisplay d : stack) {
                if (d.isValid()) d.remove();
            }
        }
        if (interaction != null && interaction.isValid()) interaction.remove();
        displaysByView.clear();
        interaction = null;
    }

    /** Re-queries entries for all views, rebuilding once if any entity was invalidated. */
    public void refresh() {
        if (!enabled) return;
        boolean broken = displaysByView.isEmpty()
                || interaction == null || !interaction.isValid()
                || displaysByView.stream().flatMap(List::stream).anyMatch(d -> !d.isValid());
        if (broken) {
            spawn();
            return;
        }
        refreshContent();
    }

    private void refreshContent() {
        for (int v = 0; v < views.size(); v++) {
            refreshSet(views.get(v), displaysByView.get(v));
        }
    }

    private void refreshSet(V view, List<TextDisplay> displays) {
        if (displays.isEmpty()) return;
        List<PlayerStatsManager.LeaderboardEntry> top = source.top(view, entries);

        for (int i = 0; i < entries; i++) {
            int displayIndex = i + 1;
            if (displayIndex >= displays.size()) break;
            TextDisplay d = displays.get(displayIndex);
            if (!d.isValid()) continue;

            if (i < top.size()) {
                PlayerStatsManager.LeaderboardEntry entry = top.get(i);
                d.text(Component.text((i + 1) + ". ", NamedTextColor.GOLD)
                        .append(Component.text(entry.name(), NamedTextColor.WHITE))
                        .append(source.value(entry)));
            } else {
                d.text(Component.text((i + 1) + ". ", NamedTextColor.GOLD)
                        .append(Component.text("---", NamedTextColor.GRAY)));
            }
        }
    }

    public boolean isInteractionEntity(Entity entity) {
        return interaction != null && interaction.equals(entity);
    }

    /** Steps a viewer's active view by {@code delta} (wrapping around all views). */
    public void step(Player player, int delta) {
        if (views.isEmpty()) return;
        int current = playerView.getOrDefault(player.getUniqueId(), 0);
        int next = Math.floorMod(current + delta, views.size());
        playerView.put(player.getUniqueId(), next);
        applyVisibility(player);
        player.sendActionBar(source.viewLabel(views.get(next)));
    }

    /** Resets a viewer's active view back to index 0 (the default view). */
    public void reset(Player player) {
        if (views.isEmpty()) return;
        playerView.put(player.getUniqueId(), 0);
        applyVisibility(player);
        player.sendActionBar(source.viewLabel(views.get(0)));
    }

    /** Drops cached view state when a player disconnects. */
    public void forget(UUID playerId) {
        playerView.remove(playerId);
    }

    public void applyVisibility(Player player) {
        if (interaction == null || displaysByView.isEmpty()) return;
        int view = playerView.computeIfAbsent(player.getUniqueId(), id -> 0);
        if (view < 0 || view >= displaysByView.size()) {
            view = 0;
            playerView.put(player.getUniqueId(), 0);
        }
        Heroes plugin = Heroes.getInstance();
        for (int v = 0; v < displaysByView.size(); v++) {
            boolean shown = v == view;
            for (TextDisplay d : displaysByView.get(v)) {
                if (shown) player.showEntity(plugin, d);
                else player.hideEntity(plugin, d);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static void spawnAll() {
        activeBoards.forEach(BoardDisplay::spawn);
    }

    public static void refreshAll() {
        activeBoards.forEach(BoardDisplay::refresh);
    }

    public static void despawnAll() {
        activeBoards.forEach(BoardDisplay::despawn);
        activeBoards.clear();
    }

    /** Routes per-board lobby interaction and join/quit visibility directly to this display. */
    public final class BoardListener implements Listener {
        @EventHandler
        public void onJoin(PlayerJoinEvent event) {
            if (enabled) applyVisibility(event.getPlayer());
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            forget(event.getPlayer().getUniqueId());
        }

        @EventHandler
        public void onRightClick(PlayerInteractEntityEvent event) {
            if (event.getHand() != EquipmentSlot.HAND) return;
            if (!isInteractionEntity(event.getRightClicked())) return;
            event.setCancelled(true);
            step(event.getPlayer(), 1);
        }

        @EventHandler
        public void onLeftClick(EntityDamageByEntityEvent event) {
            if (!(event.getDamager() instanceof Player player)) return;
            if (!isInteractionEntity(event.getEntity())) return;
            event.setCancelled(true);
            if (player.isSneaking()) reset(player);
            else step(player, -1);
        }
    }
}
