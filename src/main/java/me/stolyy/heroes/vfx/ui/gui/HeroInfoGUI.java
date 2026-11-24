package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.progression.HeroProgress;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Per-hero info sheet: stats, abilities, passives, direct select/purchase, and skin picker. */
public class HeroInfoGUI extends GUI {
    private static final int SLOT_HEAD = 4;
    private static final int SLOT_PRIMARY = 20;
    private static final int SLOT_SECONDARY = 22;
    private static final int SLOT_SMASH = 24;
    private static final int PASSIVE_ROW_START = 28;
    private static final int PASSIVE_ROW_END = 34;
    private static final int SLOT_BACK = 45;
    private static final int SLOT_COSMETICS = 53;

    private final String heroName;
    private final @Nullable GUI returnTo;

    public HeroInfoGUI(Player player, String heroName, @Nullable GUI returnTo) {
        super(player, 54, HeroConfig.formatName(heroName));
        this.heroName = heroName;
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        YamlConfiguration cfg = HeroConfig.getConfig(heroName);
        PlayerStats stats = PlayerStatsManager.getStats(player);
        boolean unlocked = HeroManager.isHeroUnlockedFor(player, heroName);

        List<Component> lore = new ArrayList<>();
        String description = cfg.getString("description", "");
        if (!description.isEmpty()) {
            lore.add(Component.text(description, NamedTextColor.GRAY));
        }
        lore.add(HeroItems.levelProgressLine(player, heroName));

        int kills = 0, wins = 0, losses = 0, deaths = 0;
        for (GameMode mode : List.of(GameMode.ONE_V_ONE, GameMode.TWO_V_TWO)) {
            HeroProgress progress = stats.getModeStats(mode).getHeroStats(heroName);
            kills += progress.totals().kills();
            wins += progress.wins();
            losses += progress.losses();
            deaths += progress.deaths();
        }
        HeroProgress custom = stats.getModeStats(GameMode.CUSTOM).getHeroStats(heroName);
        lore.add(GuiText.component("gui.hero-info.head-stats-kd", "kills", kills, "deaths", deaths));
        lore.add(GuiText.component("gui.hero-info.head-stats-wl", "wins", wins, "losses", losses));
        lore.add(GuiText.component("gui.hero-info.head-stats-custom", "count", custom.wins() + custom.losses()));
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(GuiText.component("gui.hero-info.head-select-hint"));
        } else {
            lore.addAll(HeroItems.lockRequirementLore(heroName, "gui.hero.cost-purchase"));
        }

        inventoryItems.put(SLOT_HEAD, ItemBuilder.from(unlocked ? Material.SPONGE : Material.PAPER)
                .name(GuiText.component("gui.hero.selectable-name", "hero", HeroConfig.formatName(heroName)))
                .lore(lore)
                .modelData("gui", "heroes", unlocked ? heroName.toLowerCase() : "locked")
                .build());

        inventoryItems.put(SLOT_PRIMARY, abilityItem(cfg, "abilities.primary",
                "gui.hero-info.primary-label", "primary", Material.STICK));
        inventoryItems.put(SLOT_SECONDARY, abilityItem(cfg, "abilities.secondary",
                "gui.hero-info.secondary-label", "secondary", Material.BLAZE_ROD));
        inventoryItems.put(SLOT_SMASH, abilityItem(cfg, "abilities.smash",
                "gui.hero-info.smash-label", "ultimate", Material.NETHER_STAR));

        int slot = PASSIVE_ROW_START;
        for (Map<?, ?> passive : cfg.getMapList("abilities.passives")) {
            if (slot > PASSIVE_ROW_END) break;
            Object name = passive.get("name");
            if (name == null || name.toString().isEmpty()) continue;

            List<Component> passiveLore = new ArrayList<>();
            Object desc = passive.get("description");
            if (desc != null && !desc.toString().isEmpty()) {
                passiveLore.add(Component.text(desc.toString(), NamedTextColor.GRAY));
            }
            passiveLore.add(GuiText.component("gui.hero-info.passive-label"));
            inventoryItems.put(slot, ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.hero-info.passive-name", "name", name))
                    .lore(passiveLore)
                    .modelData("gui", "ability", "passive")
                    .build());
            slot += 2;
        }

        inventoryItems.put(SLOT_BACK, backItem());
        inventoryItems.put(SLOT_COSMETICS, ItemBuilder.from(Material.PAINTING)
                .name(GuiText.component("gui.hero-info.cosmetics-name"))
                .lore(GuiText.lore("gui.hero-info.cosmetics-lore"))
                .modelData("gui", "hero-info", "cosmetics")
                .build());
    }

    private ItemStack abilityItem(YamlConfiguration cfg, String path, String labelKey,
                                  String modelTexture, Material material) {
        String name = Configs.requireString(cfg, path + ".name");
        String description = cfg.getString(path + ".description", "");
        List<Component> lore = new ArrayList<>();
        if (!description.isEmpty()) lore.add(Component.text(description, NamedTextColor.GRAY));
        lore.add(GuiText.component(labelKey));
        return ItemBuilder.from(material)
                .name(GuiText.component("gui.hero-info.active-name", "name", name))
                .lore(lore)
                .modelData("gui", "ability", modelTexture)
                .build();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_HEAD -> {
                if (HeroManager.isHeroUnlockedFor(player, heroName)) {
                    HeroItems.select(player, heroName);
                } else if (HeroItems.tryUnlock(player, heroName)) {
                    populate();
                    update();
                }
            }
            case SLOT_COSMETICS -> GUIManager.open(player, new SkinSelectGUI(player, heroName, this));
            case SLOT_BACK -> back();
        }
    }

    private void back() {
        if (returnTo != null) GUIManager.open(player, returnTo);
        else GUIManager.close(player);
    }

    @Override
    protected GUI getParentGUI() {
        return returnTo;
    }
}
