package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.MapPool;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Preset selection (B13.7, shipped incrementally): DEFAULT/BOSS/TDM/EVOLUTION now,
 * the series presets (Nuzlocke/Ironman) when Batch 8 lands. Selecting a preset calls
 * game.setGameModePreset and opens that preset's settings sub-GUI.
 */
public class GameModeGUI extends GUI {
    private static final int SLOT_DEFAULT = 0;
    private static final int SLOT_BOSS = 1;
    private static final int SLOT_TDM = 2;
    private static final int SLOT_EVOLUTION = 3;
    private static final int SLOT_SERIES = 4;
    private static final int SLOT_NUZLOCKE = 5;
    private static final int SLOT_IRONMAN = 6;
    private static final int SLOT_BACK = 22;

    private Game game;
    private final CustomGameGUI partyGUI;

    public GameModeGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(player, 27, "Select Game Mode");
        this.game = game;
        this.partyGUI = partyGUI;
    }

    /**
     * Installs the preset and, when the current map isn't in the preset's map-pool
     * (gamemodes.yml), swaps to a random pool map. changeMap builds a NEW Game, so the
     * returned instance must be what the follow-up settings GUI is opened on.
     */
    private Game applyPreset(GameModePreset preset) {
        game.setGameModePreset(preset);
        MapPool pool = GameModeConfigManager.getMapPool(preset);
        if (pool != null) {
            Set<GameMap> poolMaps = GameMapManager.getMapsForPool(pool);
            GameMap current = GameMapManager.findMapByName(game.settings().map().name());
            if (!poolMaps.isEmpty() && !poolMaps.contains(current)) {
                Game newGame = GameManager.changeMap(game, GameMapManager.getRandomMapForPool(pool));
                partyGUI.setGame(newGame);
                game = newGame;
                player.sendMessage(Component.text(
                        "Map changed to " + newGame.settings().map().name()
                                + " (from the " + preset.displayName() + " map pool)", NamedTextColor.AQUA));
            }
        }
        return game;
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_BACK, ItemBuilder.from(Material.PAPER)
                .name("<white>Back")
                .modelData("gui", "navigation", "back")
                .build());
    }

    @Override
    protected void update() {
        inventoryItems.put(SLOT_DEFAULT, presetItem(GameModePreset.DEFAULT, Material.GRAY_CONCRETE,
                "Classic: last team standing"));
        inventoryItems.put(SLOT_BOSS, presetItem(GameModePreset.BOSS, Material.RED_CONCRETE,
                "One boss vs everyone",
                "Boss: " + BossModeSettingsGUI.formatLives(GameModeConfigManager.getBossLivesPerEnemy())
                        + " lives per enemy @ " + (int) GameModeConfigManager.getBossHpPerLife() + " HP",
                "Others: " + GameModeConfigManager.getBossNonBossLives() + " life @ "
                        + (int) GameModeConfigManager.getBossNonBossHp() + " HP"));
        inventoryItems.put(SLOT_TDM, presetItem(GameModePreset.TDM, Material.BLUE_CONCRETE,
                "No eliminations",
                "First to " + GameModeConfigManager.getTdmKillLimit() + " kills",
                "Start HP: " + (int) GameModeConfigManager.getTdmStartHp(),
                "Hero re-pick on respawn"));
        inventoryItems.put(SLOT_EVOLUTION, presetItem(GameModePreset.EVOLUTION, Material.GREEN_CONCRETE,
                "Kills advance you through a hero order",
                "Kill on your FINAL hero to win",
                "Start HP: " + (int) GameModeConfigManager.getEvolutionStartHp()));
        inventoryItems.put(SLOT_SERIES, presetItem(GameModePreset.SERIES, Material.CYAN_CONCRETE,
                "Best-of-" + GameModeConfigManager.getSeriesBestOf() + " series of classic rounds",
                "Maps rotate every round",
                "Optional hero re-pick between rounds"));
        inventoryItems.put(SLOT_NUZLOCKE, presetItem(GameModePreset.NUZLOCKE, Material.PURPLE_CONCRETE,
                "Series: a hero that dies is gone",
                GameModeConfigManager.getNuzlockeStocksPerGame() + " stock per round",
                "HP carries over between rounds",
                "Run out of heroes and you're out"));
        inventoryItems.put(SLOT_IRONMAN, presetItem(GameModePreset.IRONMAN, Material.ORANGE_CONCRETE,
                "Series: winning a round CLEARS your hero",
                GameModeConfigManager.getIronmanStocksPerGame() + " stock per round",
                "Clear every hero to win the series"));
        fillEmptySlots();
        super.update();
    }

    private ItemStack presetItem(GameModePreset preset, Material material, String... loreLines) {
        ItemBuilder builder = ItemBuilder.from(material)
                .name("<gold>" + preset.displayName())
                .modelData("gui", "gamemode", preset.name().toLowerCase());
        Component[] lore = new Component[loreLines.length + (game.gameModePreset() == preset ? 1 : 0)];
        for (int i = 0; i < loreLines.length; i++) {
            lore[i] = Component.text(loreLines[i], NamedTextColor.GRAY);
        }
        if (game.gameModePreset() == preset) {
            lore[loreLines.length] = Component.text("SELECTED", NamedTextColor.GREEN);
        }
        ItemStack item = builder.lore(lore).build();
        if (game.gameModePreset() == preset) highlightItem(item);
        return item;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (game.gameState() != GameState.WAITING) {
            player.closeInventory();
            return;
        }
        switch (event.getSlot()) {
            case SLOT_DEFAULT -> {
                applyPreset(GameModePreset.DEFAULT);
                selectSound();
                GUIManager.open(player, partyGUI);
            }
            case SLOT_BOSS -> {
                Game g = applyPreset(GameModePreset.BOSS);
                selectSound();
                GUIManager.open(player, new BossModeSettingsGUI(g, player, partyGUI));
            }
            case SLOT_TDM -> {
                Game g = applyPreset(GameModePreset.TDM);
                selectSound();
                GUIManager.open(player, new TdmSettingsGUI(g, player, partyGUI));
            }
            case SLOT_EVOLUTION -> {
                Game g = applyPreset(GameModePreset.EVOLUTION);
                selectSound();
                GUIManager.open(player, new EvolutionSettingsGUI(g, player, partyGUI));
            }
            case SLOT_SERIES -> {
                Game g = applyPreset(GameModePreset.SERIES);
                selectSound();
                GUIManager.open(player, new SeriesSettingsGUI(g, player, partyGUI));
            }
            case SLOT_NUZLOCKE -> {
                Game g = applyPreset(GameModePreset.NUZLOCKE);
                selectSound();
                GUIManager.open(player, new NuzlockeSettingsGUI(g, player, partyGUI));
            }
            case SLOT_IRONMAN -> {
                Game g = applyPreset(GameModePreset.IRONMAN);
                selectSound();
                GUIManager.open(player, new IronmanSettingsGUI(g, player, partyGUI));
            }
            case SLOT_BACK -> GUIManager.open(player, partyGUI);
        }
    }

    private void selectSound() {
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
