package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class GameMapGUI extends GUI {
    private static final int MAPS_PER_PAGE = 45;
    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_FILTER = 46;
    private static final int SLOT_CANCEL = 48;
    private static final int SLOT_RANDOM = 49;
    private static final int SLOT_CONFIRM = 50;
    private static final int SLOT_NEXT_PAGE = 53;
    private final GUI parentGUI;
    private List<GameMap> filteredMaps;

    private int currentPage = 0;
    private GameMap selectedMap;
    private int filterIndex = 0;
    private final List<MapFilter> filters = List.of(
            MapFilter.V1_ONLY,
            MapFilter.V2_ONLY,
            MapFilter.V1_AND_V2,
            MapFilter.V1_OR_V2,
            MapFilter.ALL
    );
    private Game game;

    public GameMapGUI(Game game, Player player, GUI parentGUI) {
        super(player, 54, "Map Selection");
        this.game = game;
        this.parentGUI = parentGUI;
        this.selectedMap = game.settings().map();
        applyFilter();
        update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        // Select Map
        if (slot >= 0 && slot < MAPS_PER_PAGE) {
            int mapIndex = (currentPage * MAPS_PER_PAGE) + slot;
            if (mapIndex < filteredMaps.size()) {
                selectedMap = filteredMaps.get(mapIndex);
                SoundBuilder.of(Sound.UI_BUTTON_CLICK).pitch(1.0f).play(player);
                update();
            }
            return;
        }

        switch (slot) {
            // Previous Page
            case SLOT_PREV_PAGE -> {
                if (currentPage > 0) {
                    currentPage--;
                    SoundBuilder.of(Sound.UI_BUTTON_CLICK).pitch(1.0f).play(player);
                    update();
                }
            }
            // Filter
            case SLOT_FILTER -> {
                if (event.getClick() == ClickType.RIGHT) {
                    filterIndex = (filterIndex - 1 + filters.size()) % filters.size();
                } else {
                    filterIndex = (filterIndex + 1) % filters.size();
                }
                currentPage = 0;
                applyFilter();
                SoundBuilder.of(Sound.UI_BUTTON_CLICK).pitch(1.2f).play(player);
                update();
            }
            // Cancel
            case SLOT_CANCEL -> GUIManager.open(player, parentGUI);
            // Randomize Map
            case SLOT_RANDOM -> {
                if (filteredMaps.isEmpty()) {
                    player.sendMessage(Component.text("No maps available with current filter.", NamedTextColor.RED));
                    SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.5f).play(player);
                } else {
                    selectedMap = filteredMaps.get(new Random().nextInt(filteredMaps.size()));
                    player.sendMessage(Component.text("Selected random map: " + selectedMap.name(), NamedTextColor.AQUA));
                    SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
                    update();
                }
            }
            // Confirm
            case SLOT_CONFIRM -> {
                if (selectedMap != null) {
                    game = GameManager.changeMap(this.game, this.selectedMap);

                    if (parentGUI instanceof CustomGameGUI partyGUI) partyGUI.setGame(game);
                    GUIManager.open(player, parentGUI);
                    player.sendMessage(Component.text("Map changed to: " + selectedMap.name(), NamedTextColor.GREEN));
                }
            }
            // Next Page
            case SLOT_NEXT_PAGE -> {
                if ((currentPage + 1) * MAPS_PER_PAGE < filteredMaps.size()) {
                    currentPage++;
                    SoundBuilder.of(Sound.UI_BUTTON_CLICK).pitch(1.0f).play(player);
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
        MapFilter filter = filters.get(filterIndex);
        filteredMaps = new ArrayList<>(filter.filterMaps());
    }

    @Override
    protected void update() {
        inventoryItems.clear();

        inventoryItems.put(SLOT_PREV_PAGE, ItemBuilder.from(Material.PAPER)
                .name("<gray>Previous Page")
                .modelData("gui", "navigation", "prev")
                .build());

        inventoryItems.put(SLOT_FILTER, createFilterItem());

        inventoryItems.put(SLOT_CANCEL, ItemBuilder.from(Material.PAPER)
                .name("<red>Cancel")
                .modelData("gui", "general", "close")
                .build());

        inventoryItems.put(SLOT_RANDOM, ItemBuilder.from(Material.NETHER_STAR)
                .name("<gold>Select Random Map")
                .lore(
                        Component.text("Picks from: ", NamedTextColor.GRAY)
                                .append(Component.text(filters.get(filterIndex).getDisplayName(), NamedTextColor.AQUA)),
                        Component.empty(),
                        Component.text("Click to pick a random map.", NamedTextColor.YELLOW)
                )
                .modelData("gui", "maps", "random")
                .build());

        inventoryItems.put(SLOT_CONFIRM, selectedMap != null
                ? ItemBuilder.from(Material.PAPER)
                    .name("<green>Confirm Selection: " + selectedMap.name())
                    .modelData("gui", "general", "confirm")
                    .build()
                : ItemBuilder.from(Material.PAPER)
                    .name("<red>Select a map first!")
                    .modelData("gui", "general", "confirm-disabled")
                    .build());

        inventoryItems.put(SLOT_NEXT_PAGE, ItemBuilder.from(Material.PAPER)
                .name("<gray>Next Page")
                .modelData("gui", "navigation", "next")
                .build());

        int startIndex = currentPage * MAPS_PER_PAGE;
        for (int i = 0; i < MAPS_PER_PAGE; i++) {
            int mapIndex = startIndex + i;
            if (mapIndex < filteredMaps.size()) {
                GameMap map = filteredMaps.get(mapIndex);
                inventoryItems.put(i, createMapItem(map));
            } else {
                break;
            }
        }
        super.update();
    }

    private ItemStack createMapItem(GameMap map) {
        ItemStack item = map.displayItem().clone();
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(map.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            String modes = map.supportedModes().stream().map(Enum::name).collect(Collectors.joining(", "));
            lore.add(Component.text("Supported Modes: ", NamedTextColor.GRAY).append(Component.text(modes, NamedTextColor.WHITE)));
            lore.add(Component.empty());
            lore.add(Component.text("Click to select this map", NamedTextColor.YELLOW));

            meta.lore(lore);
            item.setItemMeta(meta);
        }

        // Per-map pixel-art icon (B20.3): the yml's icon-model-key replaces the bare block.
        if (map.iconModelKey() != null) {
            ItemBuilder.applyModelData(item, map.iconModelKey());
        }

        if (selectedMap != null && selectedMap.name().equals(map.name())) {
            highlightItem(item);
        }

        return item;
    }

    private ItemStack createFilterItem() {
        MapFilter currentFilter = filters.get(filterIndex);
        return ItemBuilder.from(Material.PAPER)
                .name("<gold>Filter: " + currentFilter.getDisplayName())
                .lore(
                        Component.empty(),
                        Component.text("Left-click to cycle forward", NamedTextColor.YELLOW),
                        Component.text("Right-click to cycle backward", NamedTextColor.YELLOW)
                )
                .modelData("gui", "maps", "filter")
                .build();
    }

    public enum MapFilter {
        V1_ONLY("1v1 Only") {
            @Override
            public Set<GameMap> filterMaps() {
                return GameMapManager.getMapsForMode(GameMode.ONE_V_ONE);
            }
        },
        V2_ONLY("2v2 Only") {
            @Override
            public Set<GameMap> filterMaps() {
                return GameMapManager.getMapsForMode(GameMode.TWO_V_TWO);
            }
        },
        V1_AND_V2("1v1 AND 2v2") {
            @Override
            public Set<GameMap> filterMaps() {
                Set<GameMap> v1Maps = GameMapManager.getMapsForMode(GameMode.ONE_V_ONE);
                Set<GameMap> v2Maps = GameMapManager.getMapsForMode(GameMode.TWO_V_TWO);
                Set<GameMap> result = new LinkedHashSet<>(v1Maps);
                result.retainAll(v2Maps);
                return result;
            }
        },
        V1_OR_V2("1v1 OR 2v2") {
            @Override
            public Set<GameMap> filterMaps() {
                Set<GameMap> v1Maps = GameMapManager.getMapsForMode(GameMode.ONE_V_ONE);
                Set<GameMap> v2Maps = GameMapManager.getMapsForMode(GameMode.TWO_V_TWO);
                Set<GameMap> result = new LinkedHashSet<>(v1Maps);
                result.addAll(v2Maps);
                return result;
            }
        },
        ALL("All Maps") {
            @Override
            public Set<GameMap> filterMaps() {
                Set<GameMap> all = new LinkedHashSet<>();
                for (GameMode mode : GameMode.values()) {
                    all.addAll(GameMapManager.getMapsForMode(mode));
                }
                return all;
            }
        };

        private final String displayName;

        MapFilter(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
        public abstract Set<GameMap> filterMaps();
    }
}
