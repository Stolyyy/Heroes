package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.BotConfig;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Hero selection supporting lobby selection, match/bot/override picking, and locked series
 * picks. Construct via the named static factories.
 */
public class HeroSelectGUI extends GUI {
    private static final int SIZE = 54;
    private static final int MAX_CONTENT_SLOTS = 45;
    private static final int SLOT_LEAVE_SERIES = 45;
    private static final int SLOT_RESET = 47;
    private static final int SLOT_CLOSE = 49;

    private final List<String> heroNames;
    private final Map<Integer, String> slotToHero = new HashMap<>();
    private final Set<Integer> eliminatedSlots = new HashSet<>();
    private final Set<Integer> lockedSlots = new HashSet<>();

    private final @Nullable Predicate<String> available;
    private final Set<String> eliminated;
    private final @Nullable Consumer<String> onPick;
    private final @Nullable Runnable onReset;
    private final @Nullable GUI returnTo;
    private final @Nullable Runnable onLeaveSeries;
    private final boolean respawnPick;

    private HeroSelectGUI(Player player, String title,
                          @Nullable Predicate<String> available, @Nullable Set<String> eliminated,
                          @Nullable Consumer<String> onPick, @Nullable Runnable onReset,
                          @Nullable GUI returnTo, @Nullable Runnable onLeaveSeries,
                          boolean respawnPick) {
        super(player, SIZE, title);
        this.available = available;
        this.eliminated = eliminated == null ? Set.of() : eliminated;
        this.onPick = onPick;
        this.onReset = onReset;
        this.returnTo = returnTo;
        this.onLeaveSeries = onLeaveSeries;
        this.respawnPick = respawnPick;
        this.heroNames = HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name) || player.isOp())
                .toList();
    }

    public static HeroSelectGUI lobby(Player player) {
        return new HeroSelectGUI(player, GuiText.raw("gui.hero-select.title-lobby"),
                null, null, null, null, null, null, false);
    }

    public static HeroSelectGUI lobbyNextGame(Player player) {
        return new HeroSelectGUI(player, GuiText.raw("gui.hero-select.title-next-game"),
                null, null, null, null, null, null, false);
    }

    public static HeroSelectGUI forOverride(Player player, String targetName,
                                            Consumer<String> onPick, Runnable onReset, GUI returnTo) {
        return new HeroSelectGUI(player, GuiText.raw("gui.hero-select.title-override", "target", targetName),
                null, null, onPick, onReset, returnTo, null, false);
    }

    public static HeroSelectGUI forBot(Player player, String botName,
                                       Consumer<String> onPick, GUI returnTo) {
        return new HeroSelectGUI(player, GuiText.raw("gui.hero-select.title-bot", "bot", botName),
                BotConfig::isEligibleForBots, null, onPick, null, returnTo, null, false);
    }

    public static HeroSelectGUI forRespawn(Player player, Consumer<String> onPick) {
        return new HeroSelectGUI(player, GuiText.raw("gui.hero-select.title-respawn"),
                null, null, onPick, null, null, null, true);
    }

    public static HeroSelectGUI forSeries(Player player, int roundNumber,
                                          Predicate<String> available, Set<String> eliminated,
                                          Consumer<String> onPick, Runnable onLeaveSeries) {
        return new HeroSelectGUI(player, GuiText.raw("gui.hero-select.title-series", "round", roundNumber),
                available, eliminated, onPick, null, null, onLeaveSeries, false);
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
            playSuccess();
            back();
            return;
        }

        if (eliminatedSlots.contains(slot)) {
            playError();
            return;
        }

        String heroName = slotToHero.get(slot);
        if (heroName == null) return;

        if (onPick != null) {
            onPick.accept(heroName);
            playSuccess();
            back();
            return;
        }

        if (event.getClick() == ClickType.RIGHT) {
            GUIManager.open(player, new HeroInfoGUI(player, heroName, this));
            return;
        }

        if (lockedSlots.contains(slot)) {
            if (HeroItems.tryUnlock(player, heroName)) {
                populate();
                update();
            }
            return;
        }

        HeroItems.select(player, heroName);
    }

    @Override
    protected void populate() {
        slotToHero.clear();
        eliminatedSlots.clear();
        lockedSlots.clear();

        boolean lobbyMode = onPick == null;
        List<String> ordered = heroNames.stream()
                .sorted(Comparator.comparing((String name) -> lobbyMode && !HeroManager.isHeroUnlockedFor(player, name))
                        .thenComparing(HeroConfig::formatName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int slot = 0;
        for (String heroName : ordered) {
            if (slot >= MAX_CONTENT_SLOTS) break;
            if (eliminated.contains(heroName)) continue;
            if (available != null && !available.test(heroName)) continue;

            if (lobbyMode && !HeroManager.isHeroUnlockedFor(player, heroName)) {
                inventoryItems.put(slot, HeroItems.lockedIcon(heroName));
                lockedSlots.add(slot);
            } else {
                inventoryItems.put(slot, HeroItems.selectableIcon(player, heroName, lobbyMode));
            }
            slotToHero.put(slot, heroName);
            slot++;
        }

        for (String heroName : ordered) {
            if (slot >= MAX_CONTENT_SLOTS) break;
            if (!eliminated.contains(heroName)) continue;
            inventoryItems.put(slot, HeroItems.eliminatedIcon(heroName));
            eliminatedSlots.add(slot);
            slot++;
        }

        if (onLeaveSeries != null) {
            inventoryItems.put(SLOT_LEAVE_SERIES, ItemBuilder.from(Material.RED_BED)
                    .name(GuiText.component("gui.hero-select.leave-series-name"))
                    .lore(GuiText.lore("gui.hero-select.leave-series-lore"))
                    .modelData("gui", "series", "leave")
                    .build());
        }

        if (onReset != null) {
            inventoryItems.put(SLOT_RESET, ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.hero-select.reset-name"))
                    .lore(GuiText.lore("gui.hero-select.reset-lore"))
                    .modelData("gui", "player", "reset-hero")
                    .build());
        }

        if (onLeaveSeries == null) {
            inventoryItems.put(SLOT_CLOSE, closeItem());
        }
    }

    @Override
    public boolean isLocked() {
        return onLeaveSeries != null;
    }

    public boolean isRespawnPick() {
        return respawnPick;
    }

    private void back() {
        if (returnTo != null) GUIManager.open(player, returnTo);
        else GUIManager.close(player);
    }
}
