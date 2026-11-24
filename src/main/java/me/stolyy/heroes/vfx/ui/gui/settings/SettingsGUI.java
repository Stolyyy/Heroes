package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import me.stolyy.heroes.vfx.ui.gui.GUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared base for custom-match settings GUIs: enforces the {@link GameState#WAITING} gate,
 * renders registered {@link Row}s, places the Back/Close button, and fills empty slots.
 */
public abstract class SettingsGUI extends GUI {
    protected final Game game;
    protected final CustomGameGUI parentGUI;
    private final int backSlot;
    private final boolean closeStyle;
    private final List<Row> rows = new ArrayList<>();

    protected SettingsGUI(Game game, Player player, int size, String title,
                          CustomGameGUI parentGUI, int backSlot, boolean closeStyle) {
        super(player, size, title);
        this.game = game;
        this.parentGUI = parentGUI;
        this.backSlot = backSlot;
        this.closeStyle = closeStyle;
    }

    protected void addRow(Row row) {
        rows.add(row);
    }

    @Override
    protected void populate() {}

    protected void renderExtra() {}

    protected boolean handleExtraClick(int slot, ClickType click) {
        return false;
    }

    @Override
    protected void update() {
        inventoryItems.clear();
        inventoryItems.put(backSlot, closeStyle ? closeItem() : backItem());
        for (Row row : rows) {
            if (row.visible()) {
                inventoryItems.put(row.slot(), row.render());
            }
        }
        renderExtra();
        fillEmptySlots();
        super.update();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        if (game != null && game.gameState() != GameState.WAITING) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        if (slot == backSlot) {
            GUIManager.open(player, parentGUI);
            return;
        }
        if (handleExtraClick(slot, event.getClick())) {
            return;
        }
        for (Row row : rows) {
            if (row.slot() == slot && row.visible()) {
                row.onClick(this, event.getClick());
                return;
            }
        }
    }

    public Player player() {
        return player;
    }

    public void refresh() {
        update();
    }

    public void reopen() {
        GUIManager.open(player, this);
    }

    @Override
    protected GUI getParentGUI() {
        return parentGUI;
    }
}
