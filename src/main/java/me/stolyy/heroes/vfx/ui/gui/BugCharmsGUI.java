package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.kit.bug.CharmConfig;
import me.stolyy.heroes.ability.kit.bug.Charms;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.*;

public class BugCharmsGUI extends GUI {
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_RESET = 53;
    private static final int NOTCH_ROW_SIZE = 9;

    private final Set<Charms> selectedCharms;
    private final Map<Charms, Integer> charmLocations;
    private int usedNotches = 0;

    public BugCharmsGUI(Player player) {
        super(player, 54, "Charm Selection");
        this.selectedCharms = new HashSet<>(HeroManager.getEquippedCharms(player.getUniqueId()));
        this.charmLocations = new HashMap<>();
        initCharmLocations();
        update();
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
                .name("<red>Save and Exit")
                .modelData("gui", "general", "close")
                .build());
        inventoryItems.put(SLOT_RESET, ItemBuilder.from(Material.PAPER)
                .name("<yellow>Reset Charms")
                .lore(Component.text("Unequip all charms", NamedTextColor.GRAY))
                .modelData("gui", "charms", "reset")
                .build());
    }

    @Override
    protected void update() {
        // Calculate used notches from config
        usedNotches = selectedCharms.stream()
                .mapToInt(CharmConfig::getCost)
                .sum();

        // Render notch indicators (slots 0-8)
        int totalNotches = CharmConfig.notches();
        for (int i = 0; i < NOTCH_ROW_SIZE; i++) {
            if (i < totalNotches) {
                if (i < usedNotches) {
                    inventoryItems.put(i, ItemBuilder.from(Material.PAPER)
                            .name("<gray>Used Notch")
                            .modelData("gui", "charms", "notch-used")
                            .build());
                } else {
                    inventoryItems.put(i, ItemBuilder.from(Material.PAPER)
                            .name("<white>Free Notch")
                            .modelData("gui", "charms", "notch-free")
                            .build());
                }
            } else {
                inventoryItems.remove(i);
            }
        }

        // Render charm items using config data
        for (Charms charm : Charms.values()) {
            Integer slot = charmLocations.get(charm);
            if (slot == null) continue;

            int cost = CharmConfig.getCost(charm);
            String description = CharmConfig.getDescription(charm);
            String texture = CharmConfig.getTexture(charm);

            ItemBuilder builder = ItemBuilder.from(Material.PAPER)
                    .name("<gold>" + HeroConfig.formatName(charm.name()))
                    .lore(
                            Component.text(description, NamedTextColor.GRAY),
                            Component.text("Cost: " + cost + " notches", NamedTextColor.AQUA)
                    )
                    .modelData("gui", "charms", texture);

            if (selectedCharms.contains(charm)) {
                builder.enchantGlint(true);
            }

            inventoryItems.put(slot, builder.build());
        }

        super.update();
    }

    /** Persist the selected charms. Called on exit (Save and Exit button or inventory close). */
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
            SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
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
