package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.progression.HeroLevel;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ProgressionConfig;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared hero item builders, level/unlock lore formatting, and purchase/selection actions
 * across {@link HeroSelectGUI}, {@link HeroInfoGUI}, {@link BotSettingsGUI}, and lobby tags.
 */
public final class HeroItems {

    private HeroItems() {}

    public static String formatTokens(int count) {
        return GuiText.raw(count == 1 ? "gui.common.token-singular" : "gui.common.token-plural");
    }

    public static Component levelProgressLine(Player player, String heroName) {
        HeroLevel level = PlayerStatsManager.getStats(player).getHeroLevel(heroName);
        int required = ProgressionConfig.getXpForLevel(level.level());
        if (required == Integer.MAX_VALUE) {
            return GuiText.component("gui.hero.level-max", "level", level.level());
        }
        int pct = (int) Math.round(100.0 * level.xp() / required);
        return GuiText.component("gui.hero.level-progress", Map.of(
                "level", String.valueOf(level.level()),
                "xp", String.valueOf(level.xp()),
                "required", String.valueOf(required),
                "pct", String.valueOf(pct)));
    }

    public static List<Component> lockRequirementLore(String heroName, String costKey) {
        int cost = HeroConfig.getUnlockCost(heroName);
        int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
        List<Component> lore = new ArrayList<>();
        lore.add(GuiText.component(costKey, "cost", cost, "unit", formatTokens(cost)));
        if (levelReq > 0) {
            lore.add(GuiText.component("gui.hero.level-required", "level", levelReq));
        }
        return lore;
    }

    public static ItemStack selectableIcon(Player player, String heroName, boolean lobbyHints) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(levelProgressLine(player, heroName));
        lore.add(GuiText.component("gui.hero.select-hint"));
        if (lobbyHints) {
            lore.add(GuiText.component("gui.hero.info-hint"));
        }
        return ItemBuilder.from(Material.SPONGE)
                .name(GuiText.component("gui.hero.selectable-name", "hero", HeroConfig.formatName(heroName)))
                .lore(lore)
                .modelData("gui", "heroes", heroName.toLowerCase())
                .build();
    }

    public static ItemStack lockedIcon(String heroName) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(lockRequirementLore(heroName, "gui.hero.cost-locked"));
        lore.addAll(GuiText.lore("gui.hero.locked-hints"));
        return ItemBuilder.from(Material.PAPER)
                .name(GuiText.component("gui.hero.locked-name", "hero", HeroConfig.formatName(heroName)))
                .lore(lore)
                .modelData("gui", "heroes", "locked")
                .build();
    }

    public static ItemStack eliminatedIcon(String heroName) {
        return ItemBuilder.from(Material.SPONGE)
                .name(GuiText.component("gui.hero.eliminated-name", "hero", HeroConfig.formatName(heroName)))
                .lore(Component.empty(), GuiText.component("gui.hero.eliminated-lore"))
                .modelData("gui", "heroes-eliminated", heroName.toLowerCase())
                .build();
    }

    public static ItemStack heroHead(String heroName, Component name, List<Component> lore) {
        return ItemBuilder.from(Material.PLAYER_HEAD)
                .name(name)
                .lore(lore)
                .modelData(heroName, "head", "default")
                .build();
    }

    /** Direct token unlock with Smash-level check and match-safe scoreboard refresh. */
    public static boolean tryUnlock(Player player, String heroName) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        int levelReq = HeroConfig.getSmashLevelRequirement(heroName);
        if (stats.smashLevel() < levelReq) {
            player.sendMessage(GuiText.component("gui.hero.unlock-level-required",
                    "required", levelReq, "current", stats.smashLevel()));
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return false;
        }
        int cost = HeroConfig.getUnlockCost(heroName);
        if (!stats.spendTokens(cost)) {
            player.sendMessage(GuiText.component("gui.hero.unlock-insufficient-tokens",
                    "cost", cost, "balance", stats.getTokenBalance()));
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_BASS).pitch(0.7f).play(player);
            return false;
        }
        stats.unlockHero(heroName);
        player.sendMessage(GuiText.component("gui.hero.unlock-success", Map.of(
                "cost", String.valueOf(cost),
                "unit", formatTokens(cost),
                "hero", HeroConfig.formatName(heroName),
                "balance", String.valueOf(stats.getTokenBalance()))));
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
        if (!GameManager.isPlayerInGame(player)) {
            LobbyScoreboard.apply(player);
        }
        return true;
    }

    /** Persists the lobby hero selection and opens Bug's charm editor only when out of a match. */
    public static void select(Player player, String heroName) {
        HeroManager.selectHero(player, heroName);
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
        if (heroName.equalsIgnoreCase("bug") && !GameManager.isPlayerInGame(player)) {
            GUIManager.open(player, new BugCharmsGUI(player));
        } else {
            GUIManager.close(player);
        }
    }
}
