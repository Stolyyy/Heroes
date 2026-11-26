package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.ability.kit.bug.CharmConfig;
import me.stolyy.heroes.ability.kit.bug.Charms;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Charm loadout GUI for Bug; saves changes on Exit button and on inventory close. */
public class BugCharmsGUI extends GUI {
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_RESET = 53;
    private static final int NOTCH_ROW_SIZE = 9;

    private final Set<Charms> selectedCharms;
    private final Map<Charms, Integer> charmLocations = new HashMap<>();
    private int usedNotches;

    public BugCharmsGUI(Player player) {
        super(player, 54, GuiText.raw("gui.charms.title"));
        this.selectedCharms = new HashSet<>(HeroManager.getEquippedCharms(player.getUniqueId()));
        initCharmLocations();
    }

    private void initCharmLocations() {
        charmLocations.put(Charms.DASHMASTER, 13);
        charmLocations.put(Charms.GRUBSONG, 19);
        charmLocations.put(Charms.HEAVY_BLOW, 20);
        charmLocations.put(Charms.KINGSOUL, 21);
        charmLocations.put(Charms.MARK_OF_PRIDE, 22);
        charmLocations.put(Charms.NAIL_MASTER, 23);
        charmLocations.put(Charms.QUICK_SLASH, 24);
        charmLocations.put(Charms.SHAMAN_STONE, 25);
        charmLocations.put(Charms.SHARP_SHADOW, 28);
        charmLocations.put(Charms.SOUL_EATER, 29);
        charmLocations.put(Charms.SOUL_TWISTER, 30);
        charmLocations.put(Charms.SPRINTMASTER, 31);
        charmLocations.put(Charms.STEADY_BODY, 32);
        charmLocations.put(Charms.STRENGTH, 33);
        charmLocations.put(Charms.WEAVERSONG, 34);
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_CLOSE, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.charms.save-and-exit"))
                .modelData("gui", "general", "close")
                .build());
        inventoryItems.put(SLOT_RESET, ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.charms.reset-name"))
                .lore(GuiText.lore("gui.charms.reset-lore"))
                .modelData("gui", "charms", "reset")
                .build());
    }

    @Override
    protected void update() {
        usedNotches = selectedCharms.stream()
                .mapToInt(CharmConfig::getCost)
                .sum();

        int totalNotches = CharmConfig.notches();
        for (int i = 0; i < NOTCH_ROW_SIZE; i++) {
            if (i < totalNotches) {
                boolean used = i < usedNotches;
                inventoryItems.put(i, ItemBuilder.from(Material.PAPER)
                        .name(GuiText.component(used ? "gui.charms.notch-used" : "gui.charms.notch-free"))
                        .modelData("gui", "charms", used ? "notch-used" : "notch-free")
                        .build());
            } else {
                inventoryItems.remove(i);
            }
        }

        for (Charms charm : Charms.values()) {
            Integer slot = charmLocations.get(charm);
            if (slot == null) continue;

            int cost = CharmConfig.getCost(charm);
            String description = CharmConfig.getDescription(charm);
            String texture = CharmConfig.getTexture(charm);

            ItemBuilder builder = ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.charms.charm-name", "charm", HeroConfig.formatName(charm.name())))
                    .lore(GuiText.lore("gui.charms.charm-lore", "description", description, "cost", cost))
                    .modelData("gui", "charms", texture);

            if (selectedCharms.contains(charm)) {
                builder.enchantGlint(true);
            }

            inventoryItems.put(slot, builder.build());
        }

        super.update();
    }

    private void save() {
        HeroManager.setEquippedCharms(player.getUniqueId(), selectedCharms);
    }

    @Override
    public void cleanup() {
        save();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            save();
            GUIManager.close(player);
            return;
        }

        if (slot == SLOT_RESET) {
            selectedCharms.clear();
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(1.0f).play(player);
            update();
            return;
        }

        Charms charm = getCharmFromSlot(slot);
        if (charm == null) return;

        int cost = CharmConfig.getCost(charm);
        if (selectedCharms.contains(charm)) {
            selectedCharms.remove(charm);
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(1.0f).play(player);
        } else if (CharmConfig.notches() - usedNotches >= cost) {
            selectedCharms.add(charm);
            playSuccess();
        } else {
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.5f).play(player);
        }

        update();
    }

    private Charms getCharmFromSlot(int slot) {
        for (Map.Entry<Charms, Integer> entry : charmLocations.entrySet()) {
            if (entry.getValue() == slot) {
                return entry.getKey();
            }
        }
        return null;
    }
}
