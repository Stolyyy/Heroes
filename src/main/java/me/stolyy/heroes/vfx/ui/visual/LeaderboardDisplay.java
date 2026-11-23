package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.GameMode;
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
 * Lobby leaderboard display.
 *
 * <p>Spawns two parallel stacks of {@link TextDisplay} entities at the same location — one for
 * {@link GameMode#ONE_V_ONE}, one for {@link GameMode#TWO_V_TWO}. Each player sees only one stack
 * at a time (via per-player entity visibility), and a single overlapping {@link Interaction} entity
 * lets a player toggle between modes with a left or right click. The toggle is per-player and
 * purely client-side, so two players standing at the same board can be viewing different modes.
 *
 * <p>Every spawned entity is tagged with a {@code heroes:leaderboard} persistent key. On every
 * {@link #spawn()} call we first sweep the worlds for any entity carrying that tag and remove it,
 * which cleans up leftovers from prior runs / crashes / location changes before spawning fresh.
 */
public class LeaderboardDisplay {

    /** Tag stamped on every entity we spawn so future runs can find and clean them.
     *  Package-visible so the sibling level board's purge can recognize (and spare) ours. */
    static final NamespacedKey ENTITY_TAG = new NamespacedKey("heroes", "leaderboard");

    /**
     * True if the entity carries any {@code heroes:} persistent key, i.e. another part of this
     * plugin owns it: the sibling level board, a queue NPC's text stack, a per-viewer hero level
     * tag. The untagged purge in {@code cleanupOrphans()} must skip those - the level board's box
     * reached the CUSTOM queue NPC's stack and deleted it a tick after it spawned. Package-visible
     * so both lobby boards share one definition.
     */
    static boolean heroesOwned(Entity e) {
        for (NamespacedKey key : e.getPersistentDataContainer().getKeys()) {
            if (key.getNamespace().equals("heroes")) return true;
        }
        return false;
    }

    /** Default mode shown to a player the first time their visibility is applied. */
    private static final GameMode DEFAULT_MODE = GameMode.ONE_V_ONE;

    private final List<TextDisplay> displays1v1 = new ArrayList<>();
    private final List<TextDisplay> displays2v2 = new ArrayList<>();
    private Interaction interaction;
    private final Map<UUID, GameMode> playerView = new HashMap<>();

    private boolean enabled;
    private int entries;
    private double lineSpacing;
    private Location location;
    private String titleText;

    public LeaderboardDisplay() {
        loadConfig();
    }

    private void loadConfig() {
        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");
        if (!file.exists()) {
            enabled = false;
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("leaderboard");
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
            // Not a config mistake — the configured world simply isn't loaded on this server,
            // so there is nothing to anchor the entities to. Stay dormant rather than fail boot.
            enabled = false;
            return;
        }
        location = new Location(world,
                Configs.requireDouble(locSection, "x"),
                Configs.requireDouble(locSection, "y"),
                Configs.requireDouble(locSection, "z"));
    }

    /**
     * Spawn (or respawn) the leaderboard. Always cleans up any tagged entities in the world first
     * — both ones we still hold references to and orphans left by previous runs.
     */
    public void spawn() {
        if (!enabled || location == null) return;

        // Keep the display chunk permanently loaded. The displays are non-persistent entities,
        // so a regular chunk unload (nobody near the lobby) silently discards them — this is
        // what made the leaderboard vanish mid-session. Force-loading also lets the orphan
        // sweep reach leftovers from prior runs.
        location.getChunk().setForceLoaded(true);
        location.getChunk().load();

        // Drop in-process references and sweep the world for any entity tagged from a prior run.
        despawn();
        cleanupOrphans();

        spawnDisplaySet(GameMode.ONE_V_ONE, displays1v1);
        spawnDisplaySet(GameMode.TWO_V_TWO, displays2v2);
        spawnInteraction();

        refresh();

        // Apply the default-mode visibility filter to everyone who's already online.
        for (Player p : Bukkit.getOnlinePlayers()) {
            applyVisibility(p);
        }
    }

    /**
     * Remove every entity in any loaded world that still carries our {@link #ENTITY_TAG}.
     * Catches leftovers from previous runs (including ones at a stale location after a config
     * change) that the in-process despawn can't reach. ADDITIONALLY purges any TextDisplay/
     * Interaction near the configured location regardless of tag — persistent entities from
     * builds that predate the tag (or setPersistent(false)) reload from the region file every
     * restart and would stack forever otherwise; nothing else legitimately lives there.
     */
    private void cleanupOrphans() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity e : new ArrayList<>(world.getEntities())) {
                if (e.getPersistentDataContainer().has(ENTITY_TAG, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }

        for (Entity e : location.getWorld().getNearbyEntities(location, 0.5, 6, 0.5)) {
            // The level leaderboard is a sibling board that may stand nearby — its entities are
            // its own to manage, so the untagged-purge must not eat them.
            if (heroesOwned(e)) {
                continue;
            }
            if (e instanceof TextDisplay || e instanceof Interaction) {
                e.remove();
            }
        }
    }

    private void spawnDisplaySet(GameMode mode, List<TextDisplay> targetList) {
        World world = location.getWorld();

        // Title — substitutes {mode} so a single template can label both stacks.
        String title = titleText.replace("{mode}", mode.displayName());
        TextDisplay titleDisplay = world.spawn(location, TextDisplay.class, d -> {
            d.text(MiniMessage.miniMessage().deserialize(title));
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setPersistent(false); // don't save into the chunk — a crash would otherwise leave it behind
            d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
        });
        targetList.add(titleDisplay);

        for (int i = 0; i < entries; i++) {
            Location lineLoc = location.clone().add(0, -(lineSpacing * (i + 1)), 0);
            TextDisplay line = world.spawn(lineLoc, TextDisplay.class, d -> {
                d.text(Component.empty());
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setPersistent(false); // don't save into the chunk — a crash would otherwise leave it behind
                d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
            });
            targetList.add(line);
        }
    }

    /**
     * Spawn the click-zone. Positioned at the bottom row so its upward-extending height covers
     * the title and every entry line.
     */
    private void spawnInteraction() {
        World world = location.getWorld();
        Location bottom = location.clone().subtract(0, lineSpacing * entries, 0);
        interaction = world.spawn(bottom, Interaction.class, i -> {
            i.setInteractionWidth(3.0f);
            i.setInteractionHeight((float) (lineSpacing * (entries + 1) + 0.5));
            i.setResponsive(true);
            i.setPersistent(false); // don't save into the chunk — a crash would otherwise leave it behind
            i.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
        });
    }

    /** Remove all in-process references. Safe to call multiple times. */
    public void despawn() {
        for (TextDisplay d : displays1v1) if (d.isValid()) d.remove();
        for (TextDisplay d : displays2v2) if (d.isValid()) d.remove();
        if (interaction != null && interaction.isValid()) interaction.remove();
        displays1v1.clear();
        displays2v2.clear();
        interaction = null;
    }

    /** Re-query stats for both modes and rewrite the entity text. */
    public void refresh() {
        if (!enabled) return;
        // If any display entity was discarded (chunk unload before the force-load existed,
        // an entity purge, /kill @e...), rebuild the whole board instead of writing into
        // dead entities. spawn() re-invokes refresh() once the fresh entities exist.
        boolean broken = displays1v1.isEmpty()
                || interaction == null || !interaction.isValid()
                || displays1v1.stream().anyMatch(d -> !d.isValid())
                || displays2v2.stream().anyMatch(d -> !d.isValid());
        if (broken) {
            spawn();
            return;
        }
        // Rankings come from PlayerStatsManager's in-memory board index (seeded once at startup,
        // overlaid with the live cache) — no disk walk, so refreshing at every game end is cheap
        // enough to do synchronously right here.
        refreshSet(GameMode.ONE_V_ONE, displays1v1);
        refreshSet(GameMode.TWO_V_TWO, displays2v2);
    }

    private void refreshSet(GameMode mode, List<TextDisplay> displays) {
        if (displays.isEmpty()) return;
        List<PlayerStatsManager.LeaderboardEntry> top = PlayerStatsManager.getTopPlayers(mode, entries);

        // Index 0 is the title (set at spawn, not touched here). Index 1..entries are the rows.
        for (int i = 0; i < entries; i++) {
            int displayIndex = i + 1;
            if (displayIndex >= displays.size()) break;
            TextDisplay d = displays.get(displayIndex);
            if (!d.isValid()) continue;

            if (i < top.size()) {
                PlayerStatsManager.LeaderboardEntry entry = top.get(i);
                d.text(Component.text((i + 1) + ". ", NamedTextColor.GOLD)
                        .append(Component.text(entry.name(), NamedTextColor.WHITE))
                        .append(Component.text(" - " + entry.value(), NamedTextColor.YELLOW)));
            } else {
                d.text(Component.text((i + 1) + ". ", NamedTextColor.GOLD)
                        .append(Component.text("---", NamedTextColor.GRAY)));
            }
        }
    }

    /**
     * True if the given entity is this leaderboard's click-zone Interaction.
     * Used by the listener to identify clicks on us.
     */
    public boolean isInteractionEntity(Entity e) {
        return interaction != null && interaction.equals(e);
    }

    /** Cycle the player's view between 1v1 and 2v2 and re-apply their visibility filter. */
    public void togglePlayerMode(Player player) {
        GameMode current = playerView.getOrDefault(player.getUniqueId(), DEFAULT_MODE);
        GameMode next = current == GameMode.ONE_V_ONE ? GameMode.TWO_V_TWO : GameMode.ONE_V_ONE;
        playerView.put(player.getUniqueId(), next);
        applyVisibility(player);
        player.sendActionBar(Component.text("Leaderboard: " + next.displayName(), NamedTextColor.GOLD));
    }

    /** Put the player's view back on {@link #DEFAULT_MODE}. */
    public void resetView(Player player) {
        playerView.put(player.getUniqueId(), DEFAULT_MODE);
        applyVisibility(player);
        player.sendActionBar(Component.text("Leaderboard: " + DEFAULT_MODE.displayName(), NamedTextColor.GOLD));
    }

    /**
     * Show the player only the entity stack for their preferred mode. Hides the other stack on
     * the client without touching the server-side entity (so other players are unaffected).
     */
    public void applyVisibility(Player player) {
        if (interaction == null) return; // not spawned yet
        GameMode mode = playerView.computeIfAbsent(player.getUniqueId(), id -> DEFAULT_MODE);
        Heroes plugin = Heroes.getInstance();
        if (mode == GameMode.ONE_V_ONE) {
            for (TextDisplay d : displays1v1) player.showEntity(plugin, d);
            for (TextDisplay d : displays2v2) player.hideEntity(plugin, d);
        } else {
            for (TextDisplay d : displays1v1) player.hideEntity(plugin, d);
            for (TextDisplay d : displays2v2) player.showEntity(plugin, d);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }
}
