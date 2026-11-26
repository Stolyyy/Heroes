package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.MapFilter;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/** Paginated map picker with mode filtering and random selection for custom lobbies. */
public class GameMapGUI extends GUI {
    private static final int MAPS_PER_PAGE = 45;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 46;
    private static final int SLOT_CANCEL = 48;
    private static final int SLOT_RANDOM = 49;
    private static final int SLOT_CONFIRM = 50;
    private static final int SLOT_NEXT_PAGE = 53;

    private final CustomGameGUI parentGUI;
    private final Random random = new Random();
    private final Pager pager = new Pager(MAPS_PER_PAGE);
    private List<GameMap> filteredMaps;
    private GameMap selectedMap;
    private int filterIndex;
    private Game game;

    public GameMapGUI(Game game, Player player, CustomGameGUI parentGUI) {
        super(player, 54, GuiText.raw("gui.map.title"));
        this.game = game;
        this.parentGUI = parentGUI;
        this.selectedMap = game.settings().map();
        applyFilter();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot >= 0 && slot < MAPS_PER_PAGE) {
            int mapIndex = pager.startIndex() + slot;
            if (mapIndex < filteredMaps.size()) {
                selectedMap = filteredMaps.get(mapIndex);
                playClick();
                update();
            }
            return;
        }

        MapFilter[] filters = MapFilter.values();
        switch (slot) {
            case SLOT_PREV_PAGE -> {
                if (pager.prev()) {
                    playClick();
                    update();
                }
            }
            case SLOT_FILTER -> {
                if (event.getClick() == ClickType.RIGHT) {
                    filterIndex = (filterIndex - 1 + filters.length) % filters.length;
                } else {
                    filterIndex = (filterIndex + 1) % filters.length;
                }
                pager.reset();
                applyFilter();
                SoundBuilder.of(Sound.UI_BUTTON_CLICK).pitch(1.2f).play(player);
                update();
            }
            case SLOT_CANCEL -> GUIManager.open(player, parentGUI);
            case SLOT_RANDOM -> {
                if (filteredMaps.isEmpty()) {
                    player.sendMessage(GuiText.component("gui.map.no-maps"));
                    SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.5f).play(player);
                } else {
                    selectedMap = filteredMaps.get(random.nextInt(filteredMaps.size()));
                    player.sendMessage(GuiText.component("gui.map.random-selected", "map", selectedMap.name()));
                    playSuccess();
                    update();
                }
            }
            case SLOT_CONFIRM -> {
                if (selectedMap != null) {
                    game = GameManager.changeMap(game, selectedMap);
                    parentGUI.setGame(game);
                    GUIManager.open(player, parentGUI);
                    player.sendMessage(GuiText.component("gui.map.changed", "map", selectedMap.name()));
                }
            }
            case SLOT_NEXT_PAGE -> {
                if (pager.next(filteredMaps.size())) {
                    playClick();
                    update();
                }
            }
        }
    }

    @Override
    protected void populate() {}

    @Override
    protected GUI getParentGUI() {
        return parentGUI;
    }

    private void applyFilter() {
        filteredMaps = new ArrayList<>(MapFilter.values()[filterIndex].filterMaps());
    }

    @Override
    protected void update() {
        inventoryItems.clear();

        inventoryItems.put(SLOT_PREV_PAGE, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.map.prev-page"))
                .modelData("gui", "navigation", "prev")
                .build());

        inventoryItems.put(SLOT_FILTER, createFilterItem());

        inventoryItems.put(SLOT_CANCEL, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.map.cancel"))
                .modelData("gui", "general", "close")
                .build());

        String filterLabel = GuiText.raw("gui.custom.pool." + MapFilter.values()[filterIndex].configKey());
        inventoryItems.put(SLOT_RANDOM, ItemBuilder.from(Material.NETHER_STAR)
                .name(GuiText.component("gui.map.random-name"))
                .lore(GuiText.lore("gui.map.random-lore", "filter", filterLabel))
                .modelData("gui", "maps", "random")
                .build());

        inventoryItems.put(SLOT_CONFIRM, selectedMap != null
                ? ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.map.confirm-name", "map", selectedMap.name()))
                    .modelData("gui", "general", "confirm")
                    .build()
                : ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.map.confirm-none"))
                    .modelData("gui", "general", "confirm-disabled")
                    .build());

        inventoryItems.put(SLOT_NEXT_PAGE, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.map.next-page"))
                .modelData("gui", "navigation", "next")
                .build());

        List<GameMap> pageMaps = pager.slice(filteredMaps);
        for (int i = 0; i < pageMaps.size(); i++) {
            inventoryItems.put(i, createMapItem(pageMaps.get(i)));
        }
        super.update();
    }

    private ItemStack createMapItem(GameMap map) {
        String modes = map.supportedModes().stream()
                .map(Enum::name)
                .collect(Collectors.joining(", "));
        ItemBuilder builder = ItemBuilder.from(map.displayItem())
                .name(GuiText.component("gui.map.card-name", "map", map.name()))
                .lore(GuiText.lore("gui.map.card-lore", "modes", modes));
        ItemStack item = builder.build();
        if (map.iconModelKey() != null) {
            ItemBuilder.applyModelData(item, map.iconModelKey());
        }
        if (selectedMap != null && selectedMap.name().equals(map.name())) {
            highlightItem(item);
        }
        return item;
    }

    private ItemStack createFilterItem() {
        String label = GuiText.raw("gui.custom.pool." + MapFilter.values()[filterIndex].configKey());
        return ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.map.filter-name", "filter", label))
                .lore(GuiText.lore("gui.map.filter-lore"))
                .modelData("gui", "maps", "filter")
                .build();
    }
}
