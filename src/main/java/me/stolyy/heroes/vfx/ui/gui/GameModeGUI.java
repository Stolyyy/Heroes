package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.MapPool;
import me.stolyy.heroes.game.rules.BossSetup;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.settings.BossModeSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.settings.EvolutionSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.settings.IronmanSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.settings.NuzlockeSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.settings.SeriesSettingsGUI;
import me.stolyy.heroes.vfx.ui.gui.settings.TdmSettingsGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Preset selection menu for custom matches; selecting a preset applies it and opens its settings sub-GUI. */
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
        super(player, 27, GuiText.raw("gui.gamemode.title"));
        this.game = game;
        this.partyGUI = partyGUI;
    }

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
                player.sendMessage(GuiText.component("gui.gamemode.map-changed-for-pool",
                        "map", newGame.settings().map().name(),
                        "mode", preset.displayName()));
            }
        }
        return game;
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_BACK, backItem());
    }

    @Override
    protected void update() {
        inventoryItems.put(SLOT_DEFAULT, presetItem(GameModePreset.DEFAULT, Material.GRAY_CONCRETE, Map.of()));
        inventoryItems.put(SLOT_BOSS, presetItem(GameModePreset.BOSS, Material.RED_CONCRETE, Map.of(
                "boss_lives", BossSetup.formatLives(GameModeConfigManager.getBossLivesPerEnemy()),
                "boss_hp", String.valueOf((int) GameModeConfigManager.getBossHpPerLife()),
                "other_lives", String.valueOf(GameModeConfigManager.getBossNonBossLives()),
                "other_hp", String.valueOf((int) GameModeConfigManager.getBossNonBossHp()))));
        inventoryItems.put(SLOT_TDM, presetItem(GameModePreset.TDM, Material.BLUE_CONCRETE, Map.of(
                "kill_limit", String.valueOf(GameModeConfigManager.getTdmKillLimit()),
                "start_hp", String.valueOf((int) GameModeConfigManager.getTdmStartHp()))));
        inventoryItems.put(SLOT_EVOLUTION, presetItem(GameModePreset.EVOLUTION, Material.GREEN_CONCRETE, Map.of(
                "start_hp", String.valueOf((int) GameModeConfigManager.getEvolutionStartHp()))));
        inventoryItems.put(SLOT_SERIES, presetItem(GameModePreset.SERIES, Material.CYAN_CONCRETE, Map.of(
                "best_of", String.valueOf(GameModeConfigManager.getSeriesBestOf()))));
        inventoryItems.put(SLOT_NUZLOCKE, presetItem(GameModePreset.NUZLOCKE, Material.PURPLE_CONCRETE, Map.of(
                "stocks", String.valueOf(GameModeConfigManager.getNuzlockeStocksPerGame()))));
        inventoryItems.put(SLOT_IRONMAN, presetItem(GameModePreset.IRONMAN, Material.ORANGE_CONCRETE, Map.of(
                "stocks", String.valueOf(GameModeConfigManager.getIronmanStocksPerGame()))));
        fillEmptySlots();
        super.update();
    }

    private ItemStack presetItem(GameModePreset preset, Material material, Map<String, String> vars) {
        List<Component> lore = new ArrayList<>(
                GuiText.lore("gui.gamemode.lore." + preset.name().toLowerCase(), vars));
        if (game.gameModePreset() == preset) {
            lore.add(GuiText.component("gui.gamemode.selected-lore"));
        }
        ItemStack item = ItemBuilder.from(material)
                .name("<gold>" + preset.displayName())
                .lore(lore)
                .modelData("gui", "gamemode", preset.name().toLowerCase())
                .build();
        if (game.gameModePreset() == preset) highlightItem(item);
        return item;
    }

    public static GUI settingsGuiFor(GameModePreset preset, Game game, Player player, CustomGameGUI partyGUI) {
        return switch (preset) {
            case BOSS -> new BossModeSettingsGUI(game, player, partyGUI);
            case TDM -> new TdmSettingsGUI(game, player, partyGUI);
            case EVOLUTION -> new EvolutionSettingsGUI(game, player, partyGUI);
            case SERIES -> new SeriesSettingsGUI(game, player, partyGUI);
            case NUZLOCKE -> new NuzlockeSettingsGUI(game, player, partyGUI);
            case IRONMAN -> new IronmanSettingsGUI(game, player, partyGUI);
            case DEFAULT -> partyGUI;
        };
    }

    private void openPreset(GameModePreset preset) {
        Game updated = applyPreset(preset);
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
        GUIManager.open(player, settingsGuiFor(preset, updated, player, partyGUI));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (game.gameState() != GameState.WAITING) {
            player.closeInventory();
            return;
        }
        switch (event.getSlot()) {
            case SLOT_DEFAULT -> openPreset(GameModePreset.DEFAULT);
            case SLOT_BOSS -> openPreset(GameModePreset.BOSS);
            case SLOT_TDM -> openPreset(GameModePreset.TDM);
            case SLOT_EVOLUTION -> openPreset(GameModePreset.EVOLUTION);
            case SLOT_SERIES -> openPreset(GameModePreset.SERIES);
            case SLOT_NUZLOCKE -> openPreset(GameModePreset.NUZLOCKE);
            case SLOT_IRONMAN -> openPreset(GameModePreset.IRONMAN);
            case SLOT_BACK -> GUIManager.open(player, partyGUI);
        }
    }

    @Override
    protected GUI getParentGUI() {
        return partyGUI;
    }
}
