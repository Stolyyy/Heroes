package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.HeroProgress;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-hero info sheet (R3-P12): head with description/level/record and select-or-purchase,
 * the three actives, the passives, and entry to the skin picker. Opened by right-clicking
 * a hero in {@link HeroSelectGUI} or a lobby hero NPC.
 *
 * <p>Hero and ability {@code description:} yml values are user-authored content — blank
 * ones simply render no line.
 */
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
    private final GUI returnTo; // null = close on back

    public HeroInfoGUI(Player player, String heroName, GUI returnTo) {
        super(player, 54, HeroConfig.formatName(heroName));
        this.heroName = heroName;
        this.returnTo = returnTo;
    }

    @Override
    protected void populate() {
        YamlConfiguration cfg = HeroConfig.getConfig(heroName);
        PlayerStats stats = PlayerStatsManager.getStats(player);
        boolean unlocked = HeroManager.isHeroUnlockedFor(player, heroName);

        // --- Head: description, level, aggregate 1v1+2v2 record + custom games played ---
        List<Component> lore = new ArrayList<>();
        String description = cfg.getString("description", "");
        if (!description.isEmpty()) {
            lore.add(Component.text(description, NamedTextColor.GRAY));
        }
        lore.add(levelProgressLine(player, heroName));

        int kills = 0, wins = 0, losses = 0, deaths = 0;
        for (GameMode mode : List.of(GameMode.ONE_V_ONE, GameMode.TWO_V_TWO)) {
            HeroProgress progress = stats.getModeStats(mode).getHeroStats(heroName);
            kills += progress.totals().kills();
            wins += progress.wins();
            losses += progress.losses();
            deaths += progress.deaths();
        }
        HeroProgress custom = stats.getModeStats(GameMode.CUSTOM).getHeroStats(heroName);
        lore.add(Component.text("Kills: " + kills + "  Deaths: " + deaths, NamedTextColor.WHITE));
        lore.add(Component.text("Wins: " + wins + "  Losses: " + losses, NamedTextColor.WHITE));
        lore.add(Component.text("Custom games played: " + (custom.wins() + custom.losses()), NamedTextColor.WHITE));
        lore.add(Component.empty());
        if (unlocked) {
            lore.add(Component.text("Click to select!", NamedTextColor.YELLOW));
        } else {
            int cost = HeroConfig.getUnlockCost(heroName);
            lore.add(Component.text("Locked — click to purchase ("
                    + cost + (cost == 1 ? " token)" : " tokens)"), NamedTextColor.RED));
            int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
            if (levelReq > 0) {
                lore.add(Component.text("Requires Smash Level: " + levelReq, NamedTextColor.RED));
            }
        }

        inventoryItems.put(SLOT_HEAD, ItemBuilder.from(unlocked ? Material.SPONGE : Material.PAPER)
                .name("<gold>" + HeroConfig.formatName(heroName))
                .lore(lore.toArray(new Component[0]))
                .modelData("gui", "heroes", unlocked ? heroName.toLowerCase() : "locked")
                .build());

        // --- Actives (names/descriptions straight from the hero yml) ---
        inventoryItems.put(SLOT_PRIMARY, abilityItem(cfg, "abilities.primary", "Primary", Material.STICK));
        inventoryItems.put(SLOT_SECONDARY, abilityItem(cfg, "abilities.secondary", "Secondary", Material.BLAZE_ROD));
        inventoryItems.put(SLOT_SMASH, abilityItem(cfg, "abilities.smash", "Ultimate", Material.NETHER_STAR));

        // --- Passives (yml list; unauthored names are skipped) ---
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
            passiveLore.add(Component.text("Passive", NamedTextColor.DARK_GRAY));
            inventoryItems.put(slot, ItemBuilder.from(Material.PAPER)
                    .name("<gold>" + name)
                    .lore(passiveLore.toArray(new Component[0]))
                    .modelData("gui", "ability", "passive")
                    .build());
            slot += 2;
        }

        inventoryItems.put(SLOT_BACK, ItemBuilder.from(Material.PAPER)
                .name("<white>Back")
                .modelData("gui", "navigation", "back")
                .build());
        inventoryItems.put(SLOT_COSMETICS, ItemBuilder.from(Material.PAINTING)
                .name("<light_purple>Cosmetics")
                .lore(Component.text("Pick this hero's skin", NamedTextColor.GRAY))
                .modelData("gui", "hero-info", "cosmetics")
                .build());
    }

    private ItemStack abilityItem(YamlConfiguration cfg, String path, String label, Material material) {
        String name = cfg.getString(path + ".name", "");
        String description = cfg.getString(path + ".description", "");
        List<Component> lore = new ArrayList<>();
        if (!description.isEmpty()) lore.add(Component.text(description, NamedTextColor.GRAY));
        lore.add(Component.text(label, NamedTextColor.DARK_GRAY));
        return ItemBuilder.from(material)
                .name("<gold>" + (name.isEmpty() ? label : name))
                .lore(lore.toArray(new Component[0]))
                .modelData("gui", "ability", label.toLowerCase())
                .build();
    }

    /** "Level 3 — 45/120 XP (37%)" from the hero's global level pool ("MAX" at the cap). */
    public static Component levelProgressLine(Player player, String heroName) {
        HeroLevel level = PlayerStatsManager.getStats(player).getHeroLevel(heroName);
        int required = ProgressionConfig.getXpForLevel(level.level());
        if (required == Integer.MAX_VALUE) {
            return Component.text("Level " + level.level() + " — MAX", NamedTextColor.AQUA);
        }
        int pct = (int) Math.round(100.0 * level.xp() / required);
        return Component.text("Level " + level.level() + " — " + level.xp() + "/" + required
                + " XP (" + pct + "%)", NamedTextColor.AQUA);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_HEAD -> {
                if (HeroManager.isHeroUnlockedFor(player, heroName)) {
                    HeroManager.selectHero(player, heroName);
                    SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
                    if (heroName.equalsIgnoreCase("bug")) {
                        GUIManager.open(player, new BugCharmsGUI(player));
                    } else {
                        GUIManager.close(player);
                    }
                } else {
                    attemptUnlock();
                }
            }
            case SLOT_COSMETICS -> GUIManager.open(player, new SkinSelectGUI(player, heroName, this));
            case SLOT_BACK -> back();
        }
    }

    /** Same direct-purchase flow as HeroSelectGUI (chat feedback, no confirmation GUI). */
    private void attemptUnlock() {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
        if (stats.smashLevel() < levelReq) {
            player.sendMessage(Component.text("Requires Smash Level " + levelReq + " (you're at "
                    + stats.smashLevel() + ").", NamedTextColor.RED));
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return;
        }
        int cost = HeroConfig.getUnlockCost(heroName);
        if (!stats.spendTokens(cost)) {
            player.sendMessage(Component.text("Not enough tokens — need " + cost + ", you have "
                    + stats.getTokenBalance() + ".", NamedTextColor.RED));
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return;
        }
        stats.unlockHero(heroName);
        player.sendMessage(Component.text("Spent " + cost + (cost == 1 ? " token" : " tokens")
                + " to unlock " + HeroConfig.formatName(heroName) + "! Balance: "
                + stats.getTokenBalance(), NamedTextColor.GOLD));
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
        LobbyScoreboard.apply(player); // refresh the tokens line
        populate();
        update();
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
