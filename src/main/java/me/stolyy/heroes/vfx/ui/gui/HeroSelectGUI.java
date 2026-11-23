package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Hero selection, three modes off ONE consolidated constructor (A6.3):
 * <ul>
 *   <li><b>Lobby</b> — picking persists as the player's selected hero.</li>
 *   <li><b>Pick</b> (B12.2 override) — the choice routes to {@code onPick} instead.</li>
 *   <li><b>Series</b> — availability filter + eliminated heroes rendered (X texture,
 *       unselectable) after the available ones + a leave-series button; the GUI is
 *       locked so ESC reopens it (pick or leave are the only ways out).</li>
 * </ul>
 * B25.2 later reuses the same seam for locked-hero display and purchasing.
 */
public class HeroSelectGUI extends GUI {
    private static final int SIZE = 54;
    private static final int MAX_CONTENT_SLOTS = 45; // first 5 rows
    private static final int SLOT_LEAVE_SERIES = 45;
    private static final int SLOT_RESET = 47;
    private static final int SLOT_CLOSE = 49;

    private final List<String> heroNames;
    private final Map<Integer, String> slotToHero = new HashMap<>();
    private final Set<Integer> eliminatedSlots = new HashSet<>();
    private final Set<Integer> lockedSlots = new HashSet<>(); // lobby mode: purchasable heroes

    private final Predicate<String> available;  // null = everything available
    private final Set<String> eliminated;       // rendered after available, unselectable
    private final Consumer<String> onPick;      // null = lobby mode (persist selection)
    private final Runnable onReset;             // non-null shows the reset item
    private final GUI returnTo;                 // where pick/reset/close lands; null = close
    private final Runnable onLeaveSeries;       // non-null = forced series pick (locked)

    /**
     * True only for the respawn-countdown re-pick. Game.gameTick freezes the respawn timer while
     * this GUI is open, and that must NOT apply to a lobby-mode /hero browsed mid-match (which
     * would let a dead player pause their own respawn indefinitely). Pick mode alone can't
     * distinguish the two — PlayerSettingsGUI's hero override uses the same constructor — so the
     * respawn call site sets this explicitly.
     */
    private boolean respawnPick;

    /** Lobby mode: picking persists to the player's stats as their selected hero. */
    public HeroSelectGUI(Player player) {
        this(player, "Select Your Hero");
    }

    /** Lobby mode with a custom title (e.g. "Hero for Next Game" when opened mid-match). */
    public HeroSelectGUI(Player player, String title) {
        this(player, title, null, null, null, null, null, null);
    }

    /** Pick mode (B12.2): the chosen hero is handed to {@code onPick}, nothing persists. */
    public HeroSelectGUI(Player player, String title, Consumer<String> onPick, Runnable onReset, GUI returnTo) {
        this(player, title, null, null, onPick, onReset, returnTo, null);
    }

    /** Full form (A6.3): availability filter, eliminated display, leave-series button. */
    public HeroSelectGUI(Player player, String title,
                         Predicate<String> available, Set<String> eliminated,
                         Consumer<String> onPick, Runnable onReset,
                         GUI returnTo, Runnable onLeaveSeries) {
        super(player, SIZE, title);
        this.available = available;
        this.eliminated = eliminated == null ? Set.of() : eliminated;
        this.onPick = onPick;
        this.onReset = onReset;
        this.returnTo = returnTo;
        this.onLeaveSeries = onLeaveSeries;
        this.heroNames = HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name) || player.isOp())
                .toList();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE && onLeaveSeries == null) {
            back();
            return;
        }

        if (slot == SLOT_LEAVE_SERIES && onLeaveSeries != null) {
            onLeaveSeries.run();
            return;
        }

        if (slot == SLOT_RESET && onReset != null) {
            onReset.run();
            playHeroSound();
            back();
            return;
        }

        if (eliminatedSlots.contains(slot)) {
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return;
        }

        String heroName = slotToHero.get(slot);
        if (heroName == null) return;

        if (onPick != null) {
            onPick.accept(heroName);
            playHeroSound();
            back();
            return;
        }

        // Lobby mode: right-click opens the hero info sheet (pick/series flows above keep
        // any-click = pick so a mid-game re-pick can't get lost in an info GUI).
        if (event.getClick() == org.bukkit.event.inventory.ClickType.RIGHT) {
            GUIManager.open(player, new HeroInfoGUI(player, heroName, this));
            return;
        }

        if (lockedSlots.contains(slot)) {
            attemptUnlock(heroName);
            return;
        }

        HeroManager.selectHero(player, heroName);
        playHeroSound();

        // Charms are a lobby edit: the live Bug hero latches its equipped set on first tick, but
        // CosmeticManager rebuilds the display row from the CURRENT set on every respawn, so
        // changing charms mid-match desyncs what's shown from what's actually active.
        if (heroName.equalsIgnoreCase("bug") && !GameManager.isPlayerInGame(player)) {
            GUIManager.open(player, new BugCharmsGUI(player));
        } else {
            GUIManager.close(player);
        }
    }

    @Override
    protected void populate() {
        slotToHero.clear();
        eliminatedSlots.clear();
        lockedSlots.clear();

        // Unlock gating applies to LOBBY selection only; override/series picks bring
        // their own availability rules.
        boolean lobbyMode = onPick == null;

        // Unlocked first, then A-Z by display name within each group. Re-sorted on every populate
        // so a hero just bought moves up into the unlocked group.
        List<String> ordered = heroNames.stream()
                .sorted(Comparator.comparing((String name) -> lobbyMode && !isUnlocked(name))
                        .thenComparing(HeroConfig::formatName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int slot = 0;
        for (String heroName : ordered) {
            if (slot >= MAX_CONTENT_SLOTS) break;
            if (eliminated.contains(heroName)) continue;
            if (available != null && !available.test(heroName)) continue;

            if (lobbyMode && !isUnlocked(heroName)) {
                inventoryItems.put(slot, createLockedItem(heroName));
                slotToHero.put(slot, heroName);
                lockedSlots.add(slot);
                slot++;
                continue;
            }

            inventoryItems.put(slot, ItemBuilder.from(Material.SPONGE)
                    .name("<gold>" + HeroConfig.formatName(heroName))
                    .lore(
                            Component.empty(),
                            levelProgressLine(heroName),
                            Component.text("Click to select!", NamedTextColor.YELLOW),
                            Component.text("Right-click for info & skins", NamedTextColor.GRAY)
                    )
                    .modelData("gui", "heroes", heroName.toLowerCase())
                    .build());

            slotToHero.put(slot, heroName);
            slot++;
        }

        // Eliminated heroes render AFTER the available ones — X texture, unselectable —
        // so players can track how their series is going at a glance.
        for (String heroName : ordered) {
            if (slot >= MAX_CONTENT_SLOTS) break;
            if (!eliminated.contains(heroName)) continue;

            inventoryItems.put(slot, ItemBuilder.from(Material.SPONGE)
                    .name("<dark_gray>" + HeroConfig.formatName(heroName))
                    .lore(
                            Component.empty(),
                            Component.text("Eliminated", NamedTextColor.RED)
                    )
                    .modelData("gui", "heroes-eliminated", heroName.toLowerCase()) // B19 manifest key
                    .build());

            eliminatedSlots.add(slot);
            slot++;
        }

        if (onLeaveSeries != null) {
            inventoryItems.put(SLOT_LEAVE_SERIES, ItemBuilder.from(Material.RED_BED)
                    .name("<red>Leave Series")
                    .lore(Component.text("Drops you from the series;", NamedTextColor.GRAY),
                          Component.text("the others play on.", NamedTextColor.GRAY))
                    .modelData("gui", "series", "leave")
                    .build());
        }

        if (onReset != null) {
            inventoryItems.put(SLOT_RESET, ItemBuilder.from(Material.PAPER)
                    .name("<yellow>Reset to Selected Hero")
                    .lore(Component.text("Clears the override", NamedTextColor.GRAY))
                    .modelData("gui", "player", "reset-hero")
                    .build());
        }

        if (onLeaveSeries == null) {
            inventoryItems.put(SLOT_CLOSE, ItemBuilder.from(Material.PAPER)
                    .name("<red>Close")
                    .modelData("gui", "general", "close")
                    .build());
        }
    }

    /** Series picks are mandatory: ESC just reopens the GUI (pick or leave to exit). */
    @Override
    public boolean isLocked() {
        return onLeaveSeries != null;
    }

    /** Marks this instance as the respawn-countdown re-pick. Set by RespawnAction.openPickGui only. */
    public HeroSelectGUI markRespawnPick() {
        this.respawnPick = true;
        return this;
    }

    public boolean isRespawnPick() {
        return respawnPick;
    }

    private boolean isUnlocked(String heroName) {
        return HeroManager.isHeroUnlockedFor(player, heroName);
    }

    /** "Level 3 — 45/120 XP (37%)" — shared with the hero info sheet. */
    private Component levelProgressLine(String heroName) {
        return HeroInfoGUI.levelProgressLine(player, heroName);
    }

    private org.bukkit.inventory.ItemStack createLockedItem(String heroName) {
        int cost = HeroConfig.getUnlockCost(heroName);
        int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Locked — Cost: " + cost + (cost == 1 ? " token" : " tokens"), NamedTextColor.RED));
        if (levelReq > 0) {
            lore.add(Component.text("Requires Smash Level: " + levelReq, NamedTextColor.RED));
        }
        lore.add(Component.text("Click to unlock!", NamedTextColor.YELLOW));
        lore.add(Component.text("Right-click for info & skins", NamedTextColor.GRAY));
        return ItemBuilder.from(Material.PAPER)
                .name("<gray>" + HeroConfig.formatName(heroName))
                .lore(lore.toArray(new Component[0]))
                .modelData("gui", "heroes", "locked")
                .build();
    }

    /** Direct purchase on click (user decision: no confirmation GUI, just chat feedback). */
    private void attemptUnlock(String heroName) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
        if (stats.smashLevel() < levelReq) {
            player.sendMessage(Component.text("Requires Smash Level " + levelReq + " (you're at "
                    + stats.smashLevel() + ").", NamedTextColor.RED));
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return;
        }
        int cost = HeroConfig.getUnlockCost(heroName);
        if (!stats.spendTokens(cost)) {
            player.sendMessage(Component.text("Not enough tokens — need " + cost + ", you have "
                    + stats.getTokenBalance() + ".", NamedTextColor.RED));
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return;
        }
        stats.unlockHero(heroName);
        player.sendMessage(Component.text("Spent " + cost + (cost == 1 ? " token" : " tokens")
                + " to unlock " + HeroConfig.formatName(heroName) + "! Balance: "
                + stats.getTokenBalance(), NamedTextColor.GOLD));
        playHeroSound();
        // Refresh the tokens line — but only in the lobby; in-game this would swap the match
        // sidebar for the lobby one, and the GUI is now reachable mid-match.
        if (!GameManager.isPlayerInGame(player)) LobbyScoreboard.apply(player);
        populate();
        update();
    }

    private void back() {
        if (returnTo != null) GUIManager.open(player, returnTo);
        else GUIManager.close(player);
    }

    private void playHeroSound() {
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
    }
}
