package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** One-row skin picker populated from non-hidden entries under the hero's {@code skins:} YML section. */
public class SkinSelectGUI extends GUI {
    private static final int SLOT_BACK = 8;

    private final String heroName;
    private final @Nullable GUI returnTo;
    private final Map<Integer, String> slotToSkin = new HashMap<>();
    private final Set<Integer> lockedSlots = new HashSet<>();

    public SkinSelectGUI(Player player, String heroName, @Nullable GUI returnTo) {
        super(player, 9, GuiText.raw("gui.skins.title", "hero", HeroConfig.formatName(heroName)));
        this.heroName = heroName;
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        slotToSkin.clear();
        lockedSlots.clear();

        PlayerStats stats = PlayerStatsManager.getStats(player);
        String selected = stats.getSkinForHero(heroName);
        int heroLevel = stats.getHeroLevel(heroName).level();
        int masterLevel = ProgressionConfig.getMasterSkinLevel();

        ConfigurationSection skins = HeroConfig.getConfig(heroName).getConfigurationSection("skins");
        int slot = 0;
        if (skins != null) {
            for (String skinId : skins.getKeys(false)) {
                if (slot >= SLOT_BACK) break;
                ConfigurationSection skinSec = skins.getConfigurationSection(skinId);
                if (skinSec != null && skinSec.getBoolean("hidden", false)) continue;

                boolean isMaster = skinId.equalsIgnoreCase("masterskin");
                boolean owned = !isMaster || heroLevel >= masterLevel;
                boolean isSelected = skinId.equalsIgnoreCase(selected);

                Component statusLore = isSelected
                        ? GuiText.component("gui.skins.selected-lore")
                        : owned
                            ? GuiText.component("gui.skins.select-lore")
                            : GuiText.component("gui.skins.locked-lore", "level", masterLevel);

                ItemStack item = ItemBuilder.from(owned ? Material.SPONGE : Material.GRAY_DYE)
                        .name(GuiText.component(owned ? "gui.skins.item-name-owned" : "gui.skins.item-name-locked",
                                "skin", formatSkinName(skinId)))
                        .lore(statusLore)
                        .modelData(HeroConfig.assetKey(heroName), "head", skinId)
                        .build();
                if (isSelected) highlightItem(item);

                inventoryItems.put(slot, item);
                slotToSkin.put(slot, skinId);
                if (!owned) lockedSlots.add(slot);
                slot++;
            }
        }

        inventoryItems.put(SLOT_BACK, backItem());
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == SLOT_BACK) {
            if (returnTo != null) GUIManager.open(player, returnTo);
            else GUIManager.close(player);
            return;
        }

        String skinId = slotToSkin.get(slot);
        if (skinId == null) return;
        if (lockedSlots.contains(slot)) {
            playError();
            return;
        }

        PlayerStatsManager.getStats(player).setSkinForHero(heroName, skinId);
        player.sendMessage(GuiText.component("gui.skins.changed",
                "hero", HeroConfig.formatName(heroName),
                "skin", formatSkinName(skinId)));
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
        populate();
        update();
    }

    private String formatSkinName(String skinId) {
        if (skinId.equalsIgnoreCase("masterskin")) return GuiText.raw("gui.skins.master-name");
        return skinId.substring(0, 1).toUpperCase() + skinId.substring(1).toLowerCase();
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
