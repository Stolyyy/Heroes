package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class GUI implements InventoryHolder {

    protected final int size;
    protected Map<Integer, ItemStack> inventoryItems;
    protected Inventory inventory;
    protected Player player;

    public GUI(Player player, int size, String title) {
        this(player, size, Component.text(title));
    }

    public GUI(Player player, int size, Component title) {
        this.player = player;
        this.size = size;
        this.inventoryItems = new HashMap<>(size);
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        populate();
        update();
        player.openInventory(inventory);
    }

    public abstract void handleClick(InventoryClickEvent event);

    protected abstract void populate();

    protected void update() {
        inventory.clear();
        for (Map.Entry<Integer, ItemStack> entry : inventoryItems.entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }
    }

    public void cleanup() { }

    public boolean isLocked() {
        return false;
    }

    protected GUI getParentGUI() {
        return null;
    }

    /** Shared background filler textured via the {@code gui_general_filler} model key. */
    protected ItemStack fillerItem() {
        return ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                .name(Component.empty())
                .modelData("gui", "general", "filler")
                .build();
    }

    protected ItemStack backItem() {
        return ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.nav.back"))
                .modelData("gui", "navigation", "back")
                .build();
    }

    protected ItemStack closeItem() {
        return ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.nav.close"))
                .modelData("gui", "general", "close")
                .build();
    }

    /** Fills every slot without content with the background filler. Call before {@code super.update()}. */
    protected void fillEmptySlots() {
        ItemStack filler = fillerItem();
        for (int i = 0; i < size; i++) {
            inventoryItems.putIfAbsent(i, filler);
        }
    }

    protected ItemStack createPlayerHead(Player target) {
        return playerHead(target, List.of());
    }

    protected ItemStack playerHead(Player target, List<Component> lore) {
        ItemStack head = ItemBuilder.from(Material.PLAYER_HEAD)
                .name(Component.text(target.getName(), NamedTextColor.GOLD))
                .lore(lore)
                .build();
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
        return head;
    }

    protected ItemStack botHead(GameBot bot, List<Component> lore) {
        return ItemBuilder.from(Material.PLAYER_HEAD)
                .name(GuiText.component("gui.custom.bot-head-name", "name", bot.getName()))
                .lore(lore)
                .build();
    }

    protected void highlightItem(ItemStack item) {
        if (item == null) return;
        item.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
    }

    public void playClick() {
        SoundBuilder.of(Sound.UI_BUTTON_CLICK).pitch(1.0f).play(player);
    }

    public void playConfirm() {
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
    }

    public void playPromptConfirm() {
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.2f).play(player);
    }

    public void playError() {
        SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
    }

    public void playSuccess() {
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
    }
}
