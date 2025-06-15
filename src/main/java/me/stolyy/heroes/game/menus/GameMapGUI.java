package me.stolyy.heroes.game.menus;

import me.stolyy.heroes.game.minigame.Game;
import me.stolyy.heroes.game.minigame.GameEnums;
import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.game.minigame.GameManager;
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
    private final GUI parentGUI;
    private final List<GameMap> availableMaps;

    private int currentPage = 0;
    private GameMap selectedMap;
    private int randomModeIndex = 0;
    private final List<RandomMapMode> randomModes = List.of(RandomMapMode.V1, RandomMapMode.V2, RandomMapMode.V1_AND_V2);
    private Game game;

    public GameMapGUI(Game game, Player player, GUI parentGUI){
        super(player, 54, "Map Selection");
        this.game = game;
        this.parentGUI = parentGUI;
        this.availableMaps = new ArrayList<>(GameMapManager.getMapsForMode(GameEnums.GameMode.PARTY));
        this.selectedMap = game.settings().map();
    }


    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        //Select Map
        if (slot >= 0 && slot < MAPS_PER_PAGE) {
            int mapIndex = (currentPage * MAPS_PER_PAGE) + slot;
            if (mapIndex < availableMaps.size()) {
                selectedMap = availableMaps.get(mapIndex);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                update();
            }
            return;
        }

        switch (slot) {
            //Previous Page
            case 45 -> {
                if (currentPage > 0) {
                    currentPage--;
                    update();
                }
            }
            //Cancel
            case 48 -> GUIManager.open(player, parentGUI);
            //Randomize Map
            case 49 -> {
                if (event.getClick() == ClickType.RIGHT) {
                    randomModeIndex = (randomModeIndex + 1) % randomModes.size();
                    update();
                } else {
                    RandomMapMode mode = randomModes.get(randomModeIndex);
                    selectedMap = GameMapManager.getRandomMapInAll(mode.getGameModes());
                    if (selectedMap != null) {
                        player.sendMessage(Component.text("Selected random map: " + selectedMap.name(), NamedTextColor.AQUA));
                        update();
                    } else {
                        player.sendMessage(Component.text("No maps found for the selected random mode.", NamedTextColor.RED));
                    }
                }
            }
            //Confirm
            case 50 -> {
                if (selectedMap != null) {
                    game = GameManager.changeMap(this.game, this.selectedMap);

                    if (parentGUI instanceof PartyModeGUI partyGUI) partyGUI.setGame(game);
                    GUIManager.open(player, parentGUI);
                    player.sendMessage(Component.text("Map changed to: " + selectedMap.name(), NamedTextColor.GREEN));
                }
            }
            //Next Page
            case 53 -> {
                if ((currentPage + 1) * MAPS_PER_PAGE < availableMaps.size()) {
                    currentPage++;
                    update();
                }
            }
        }
    }

    @Override
    protected void populate() {}

    @Override
    protected void update() {
        inventoryItems.clear();

        inventoryItems.put(45, createItem(Material.ARROW, "Previous Page"));
        inventoryItems.put(48, createItem(Material.BARRIER, "Cancel"));
        inventoryItems.put(49, createRandomMapItem());
        inventoryItems.put(50, selectedMap != null ? createItem(Material.LIME_STAINED_GLASS_PANE, "Confirm Selection: " + selectedMap.name()) : createItem(Material.RED_STAINED_GLASS_PANE, "Select a map first!"));
        inventoryItems.put(53, createItem(Material.ARROW, "Next Page"));

        int startIndex = currentPage * MAPS_PER_PAGE;
        for (int i = 0; i < MAPS_PER_PAGE; i++) {
            int mapIndex = startIndex + i;
            if (mapIndex < availableMaps.size()) {
                GameMap map = availableMaps.get(mapIndex);
                inventoryItems.put(i, createMapItem(map));
            } else {
                break;
            }
        }
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

        if (selectedMap != null && selectedMap.name().equals(map.name())) {
            highlightItem(item);
        }

        return item;
    }
    private ItemStack createRandomMapItem() {
        RandomMapMode currentMode = randomModes.get(randomModeIndex);
        ItemStack item = createItem(Material.NETHER_STAR, "Select Random Map");
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Current Mode: ", NamedTextColor.GRAY).append(Component.text(currentMode.getDisplayName(), NamedTextColor.AQUA)));
            lore.add(Component.empty());
            lore.add(Component.text("Left-click to pick a random map.", NamedTextColor.YELLOW));
            lore.add(Component.text("Right-click to cycle modes.", NamedTextColor.YELLOW));
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private enum RandomMapMode {
        V1("1v1", Set.of(GameEnums.GameMode.ONE_V_ONE)),
        V2("2v2", Set.of(GameEnums.GameMode.TWO_V_TWO)),
        V1_AND_V2("1v1 & 2v2", Set.of(GameEnums.GameMode.ONE_V_ONE, GameEnums.GameMode.TWO_V_TWO));

        private final String displayName;
        private final Set<GameEnums.GameMode> gameModes;

        RandomMapMode(String displayName, Set<GameEnums.GameMode> gameModes) {
            this.displayName = displayName;
            this.gameModes = gameModes;
        }

        public String getDisplayName() { return displayName; }
        public Set<GameEnums.GameMode> getGameModes() { return gameModes; }
    }
}
