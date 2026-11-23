package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Edits PlayerSettings at one of two scopes:
 * <ul>
 *   <li><b>Team scope</b> (team-header click): edits fan out to every GamePlayer on the
 *       team. The GUI displays a local copy seeded from the first member.</li>
 *   <li><b>Per-player scope</b> (right-click a player head): edits the target GamePlayer's
 *       own settings copy in place, and adds the Change Hero override controls.</li>
 * </ul>
 */
public class PlayerSettingsGUI extends AbstractPlayerSettingsGUI {
    private static final int SIZE = 36;
    private static final int SLOT_HERO_DISPLAY = 4;
    private static final int SLOT_CHANGE_HERO = 22;
    private static final int SLOT_CLOSE = 31;

    private final Team team;             // team scope; null in per-player scope
    private final GamePlayer gamePlayer; // per-player scope; null in team scope

    /** Team scope: broad-strokes edit applied to every member of the team. */
    public PlayerSettingsGUI(Team team, Player player, CustomGameGUI partyGUI) {
        super(player, SIZE, team.color().displayName() + " Team Settings",
                teamDisplayCopy(partyGUI.getGame(), team), partyGUI);
        this.team = team;
        this.gamePlayer = null;
        update();
    }

    /** Per-player scope: edits the target's own GamePlayer settings in place. */
    public PlayerSettingsGUI(Game game, UUID target, Player player, CustomGameGUI partyGUI) {
        super(player, SIZE, "Player Settings: " + targetName(target),
                game.getGamePlayer(target).settings(), partyGUI);
        this.team = null;
        this.gamePlayer = game.getGamePlayer(target);
        update();
    }

    /** The team GUI renders the first member's settings (or game defaults for an empty team). */
    private static PlayerSettings teamDisplayCopy(Game game, Team team) {
        for (UUID uuid : team.playerUUIDs()) {
            return new PlayerSettings(game.getGamePlayer(uuid).settings());
        }
        return new PlayerSettings(game.defaultTeamSettings());
    }

    private static String targetName(UUID target) {
        Player p = Bukkit.getPlayer(target);
        return p != null ? p.getName() : "Unknown";
    }

    @Override
    protected void applySetting(Consumer<PlayerSettings> change) {
        change.accept(settings);
        if (team != null) game.applySettings(team.color(), change);
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        switch (event.getSlot()) {
            case SLOT_LIVES -> handleLivesClick(event.getClick());
            case SLOT_HEALTH -> handleHealthClick(event.getClick());
            case SLOT_ULTIMATES -> handleUltimatesClick();
            case SLOT_FRIENDLY_FIRE -> handleFriendlyFireClick();
            case SLOT_RANDOM_HEROES -> handleRandomHeroesClick();
            case SLOT_CHANGE_HERO -> { if (gamePlayer != null) handleChangeHeroClick(event.getClick()); }
            case SLOT_CLOSE -> GUIManager.open(player, partyGUI);
        }
    }

    @Override
    protected void populate() {
        inventoryItems.put(SLOT_CLOSE, ItemBuilder.from(Material.PAPER)
                .name("<red>Save & Close")
                .modelData("gui", "general", "close")
                .build());
    }

    @Override
    protected void update() {
        if (gamePlayer != null) {
            inventoryItems.put(SLOT_HERO_DISPLAY, createHeroDisplay());
            inventoryItems.put(SLOT_CHANGE_HERO, ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
                    .name("<green>Change Hero")
                    .lore(Component.text("Left-click: pick an override hero", NamedTextColor.GRAY),
                          Component.text("Right-click: reset to their selection", NamedTextColor.GRAY))
                    .modelData("gui", "player", "change-hero")
                    .build());
        }
        super.update();
    }

    /**
     * B12.2 — one slot: left-click opens the hero select GUI in override mode,
     * right-click clears the override back to the player's persistent selection.
     * The override wins over Random Heroes (encoded in GamePlayer.resolveHeroName()).
     */
    private void handleChangeHeroClick(ClickType click) {
        if (!isGameStillWaiting()) { player.closeInventory(); return; }
        if (click == ClickType.RIGHT) {
            gamePlayer.clearHeroOverride();
            SoundBuilder.of(Sound.BLOCK_NOTE_BLOCK_PLING).pitch(1.0f).play(player);
            update();
            return;
        }
        GUIManager.open(player, new HeroSelectGUI(player,
                "Hero Override: " + targetName(gamePlayer.uuid()),
                gamePlayer::setHeroOverride,
                gamePlayer::clearHeroOverride,
                this));
    }

    private ItemStack createHeroDisplay() {
        String effective;
        NamedTextColor color;
        if (gamePlayer.heroOverride() != null) {
            effective = HeroConfig.formatName(gamePlayer.heroOverride()) + " (override)";
            color = NamedTextColor.GREEN;
        } else if (settings.randomizeHeroes()) {
            effective = "Random";
            color = NamedTextColor.LIGHT_PURPLE;
        } else {
            Player target = Bukkit.getPlayer(gamePlayer.uuid());
            String selected = target != null ? HeroManager.getHeroName(target) : null;
            effective = selected != null ? HeroConfig.formatName(selected) : "None selected";
            color = NamedTextColor.GOLD;
        }

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(effective, color)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Hero this game", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
