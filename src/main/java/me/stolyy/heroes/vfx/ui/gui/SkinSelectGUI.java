package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.command.general.SkinCommand;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.visual.HeroNpcSkins;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One-row skin picker populated from non-hidden entries under the hero's {@code skins:} YML section. */
public class SkinSelectGUI extends GUI {
    private static final int SLOT_BACK = 8;

    private final String heroName;
    private final @Nullable GUI returnTo;
    private final Map<Integer, HeroSkin> slotToSkin = new HashMap<>();
    private final Set<Integer> lockedSlots = new HashSet<>();

    public SkinSelectGUI(Player player, String heroName, @Nullable GUI returnTo) {
        super(player, 9, GuiText.raw("gui.skins.title", "hero", HeroConfig.formatName(heroName)));
        this.heroName = heroName;
        this.returnTo = returnTo;
    }

    /** Clears any session preview for {@code heroName}, persists the owned selection, refreshes NPCs, and plays feedback. */
    public static void applySelection(Player player, String heroName, HeroSkin skin) {
        CosmeticManager.clearPreview(player.getUniqueId(), heroName);
        PlayerStatsManager.getStats(player).setSkinForHero(heroName, skin.getId());
        HeroNpcSkins.refresh(player, heroName);
        player.sendMessage(GuiText.component("gui.skins.changed",
                "hero", HeroConfig.formatName(heroName),
                "skin", skin.getDisplayName()));
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
    }

    /** Activates a session-only preview for an unowned skin without touching ownership or saved selection. */
    public static void applyPreview(Player player, String heroName, HeroSkin skin) {
        CosmeticManager.setPreview(player.getUniqueId(), heroName, skin.getId());
        HeroNpcSkins.refresh(player, heroName);
        player.sendMessage(GuiText.component("gui.skins.previewed",
                "hero", HeroConfig.formatName(heroName),
                "skin", skin.getDisplayName()));
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.4f).play(player);
    }

    @Override
    protected void populate() {
        slotToSkin.clear();
        lockedSlots.clear();

        PlayerStats stats = PlayerStatsManager.getStats(player);
        HeroSkin resolved = CosmeticManager.resolveOwnedSkin(stats, heroName);
        String selected = resolved != null ? resolved.getId() : "default";
        String previewed = CosmeticManager.getPreview(player.getUniqueId(), heroName);
        boolean canPreview = player.hasPermission(SkinCommand.PREVIEW_PERMISSION);
        int masterLevel = ProgressionConfig.getMasterSkinLevel();

        int slot = 0;
        for (HeroSkin skin : CosmeticManager.selectableSkins(heroName)) {
            if (slot >= SLOT_BACK) break;

            String skinId = skin.getId();
            boolean owned = stats.ownsSkin(heroName, skinId);
            boolean isSelected = skinId.equalsIgnoreCase(selected);
            boolean isPreviewed = previewed != null && skinId.equalsIgnoreCase(previewed);

            List<Component> lore = new ArrayList<>();
            if (skin.getRarity() != null) {
                lore.add(GuiText.component("gui.skins.rarity." + skin.getRarity().name().toLowerCase(Locale.ROOT)));
            }
            if (isPreviewed) {
                lore.add(GuiText.component("gui.skins.previewing-lore"));
            } else if (isSelected) {
                lore.add(GuiText.component("gui.skins.selected-lore"));
            } else if (owned) {
                lore.add(GuiText.component("gui.skins.select-lore"));
            } else if (skinId.equalsIgnoreCase("masterskin")) {
                lore.add(GuiText.component("gui.skins.locked-lore", "level", masterLevel));
            } else {
                lore.add(GuiText.component("gui.skins.locked-unowned-lore"));
            }
            if (!owned && !isPreviewed && canPreview) {
                lore.add(GuiText.component("gui.skins.preview-hint-lore"));
            }

            Material material = (owned || isPreviewed) ? Material.SPONGE : Material.GRAY_DYE;
            String nameKey = isPreviewed
                    ? "gui.skins.item-name-preview"
                    : owned ? "gui.skins.item-name-owned" : "gui.skins.item-name-locked";

            ItemStack item = ItemBuilder.from(material)
                    .name(GuiText.component(nameKey, "skin", skin.getDisplayName()))
                    .lore(lore)
                    .modelData(HeroConfig.assetKey(heroName), "head", skinId)
                    .build();
            if (isPreviewed || (isSelected && previewed == null)) highlightItem(item);

            inventoryItems.put(slot, item);
            slotToSkin.put(slot, skin);
            if (!owned) lockedSlots.add(slot);
            slot++;
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

        HeroSkin skin = slotToSkin.get(slot);
        if (skin == null) return;

        boolean owned = PlayerStatsManager.getStats(player).ownsSkin(heroName, skin.getId());
        if (!owned) {
            if (event.isRightClick() && player.hasPermission(SkinCommand.PREVIEW_PERMISSION)) {
                applyPreview(player, heroName, skin);
                populate();
                update();
                return;
            }
            playError();
            if (!lockedSlots.contains(slot)) {
                populate();
                update();
            }
            return;
        }

        applySelection(player, heroName, skin);
        populate();
        update();
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
