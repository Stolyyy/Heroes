package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One-row skin picker (R3-P12): one item per key under the hero's yml {@code skins:}
 * section. The master skin is level-gated (hero level >= master-skin-level); everything
 * else is freely selectable. First-ever skin UI — selection storage (PlayerStats
 * selected-skins) and application (CosmeticManager.apply) already existed.
 */
public class SkinSelectGUI extends GUI {
    private static final int SLOT_BACK = 8;

    private final String heroName;
    private final GUI returnTo;
    private final Map<Integer, String> slotToSkin = new HashMap<>();
    private final Set<Integer> lockedSlots = new HashSet<>();

    public SkinSelectGUI(Player player, String heroName, GUI returnTo) {
        super(player, 9, HeroConfig.formatName(heroName) + " Skins");
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
                // Ability-owned skins (Karakot's ult, Pochita's hell form) are transient swaps the
                // kit applies and reverts itself. Listing them made them selectable, and a selected
                // one persists — so it came back as the player's real skin from spawn, permanently.
                if (skins.getConfigurationSection(skinId).getBoolean("hidden", false)) continue;
                boolean isMaster = skinId.equalsIgnoreCase("masterskin");
                boolean owned = !isMaster || heroLevel >= masterLevel;
                boolean isSelected = skinId.equalsIgnoreCase(selected);

                Component statusLore = isSelected
                        ? Component.text("Selected", NamedTextColor.GREEN)
                        : owned
                            ? Component.text("Click to select", NamedTextColor.YELLOW)
                            : Component.text("Unlocks at hero level " + masterLevel, NamedTextColor.RED);

                ItemStack item = ItemBuilder.from(owned ? Material.SPONGE : Material.GRAY_DYE)
                        .name((owned ? "<gold>" : "<gray>") + formatSkinName(skinId))
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

        inventoryItems.put(SLOT_BACK, ItemBuilder.from(Material.PAPER)
                .name("<white>Back")
                .modelData("gui", "navigation", "back")
                .build());
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
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return;
        }

        PlayerStatsManager.getStats(player).setSkinForHero(heroName, skinId);
        player.sendMessage(Component.text(HeroConfig.formatName(heroName) + " skin set to "
                + formatSkinName(skinId) + ".", NamedTextColor.GREEN));
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.2f).play(player);
        populate();
        update();
    }

    private String formatSkinName(String skinId) {
        if (skinId.equalsIgnoreCase("masterskin")) return "Master Skin";
        return skinId.substring(0, 1).toUpperCase() + skinId.substring(1).toLowerCase();
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
