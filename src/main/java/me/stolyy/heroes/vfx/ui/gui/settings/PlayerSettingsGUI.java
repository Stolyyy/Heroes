package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Edits {@link PlayerSettings} at either team scope (header click, fanning edits to every active
 * teammate) or per-player scope (head right-click, including hero override controls).
 */
public class PlayerSettingsGUI extends SettingsGUI {
    private static final int SIZE = 36;
    private static final int SLOT_HERO_DISPLAY = 4;
    private static final int SLOT_CHANGE_HERO = 22;
    private static final int SLOT_CLOSE = 31;

    private final PlayerSettings displaySettings;
    private final GamePlayer gamePlayer;

    /** Team scope: edits fan out to every active member of {@code team}. */
    public PlayerSettingsGUI(Team team, Player player, CustomGameGUI partyGUI) {
        super(partyGUI.getGame(), player, SIZE,
                GuiText.raw("gui.settings.player.team-title", "team", team.color().displayName()),
                partyGUI, SLOT_CLOSE, true);
        this.displaySettings = teamDisplayCopy(partyGUI.getGame(), team);
        this.gamePlayer = null;

        Target<PlayerSettings> target = new Target<>(
                () -> displaySettings,
                change -> {
                    change.apply(displaySettings);
                    game.applySettings(team.color(), change::apply);
                });
        PlayerRows.addAll(this, game, target);
    }

    /** Per-player scope: edits the target {@link GamePlayer}'s own settings in place. */
    public PlayerSettingsGUI(Game game, UUID targetUuid, Player player, CustomGameGUI partyGUI) {
        super(game, player, SIZE,
                GuiText.raw("gui.settings.player.player-title", "player", targetName(targetUuid)),
                partyGUI, SLOT_CLOSE, true);
        this.gamePlayer = game.getGamePlayer(targetUuid);
        this.displaySettings = gamePlayer.settings();

        Target<PlayerSettings> target = new Target<>(
                () -> displaySettings,
                change -> change.apply(displaySettings));
        PlayerRows.addAll(this, game, target);

        addRow(Row.display(SLOT_HERO_DISPLAY, this::renderHeroDisplay));
        addRow(Row.action(SLOT_CHANGE_HERO, this::renderChangeHero, this::handleChangeHero));
    }

    private ItemStack renderHeroDisplay() {
        Component name;
        if (gamePlayer.heroOverride() != null) {
            name = GuiText.component("gui.settings.player.hero-display.override-name",
                    "hero", HeroConfig.formatName(gamePlayer.heroOverride()));
        } else if (displaySettings.randomizeHeroes()) {
            name = GuiText.component("gui.settings.player.hero-display.random-name");
        } else {
            Player target = Bukkit.getPlayer(gamePlayer.uuid());
            String selected = target != null ? HeroManager.getHeroName(target) : null;
            name = selected != null
                    ? GuiText.component("gui.settings.player.hero-display.selected-name",
                            "hero", HeroConfig.formatName(selected))
                    : GuiText.component("gui.settings.player.hero-display.none-selected");
        }
        return ItemBuilder.from(Material.PLAYER_HEAD)
                .name(name)
                .lore(GuiText.lore("gui.settings.player.hero-display.lore"))
                .build();
    }

    private ItemStack renderChangeHero() {
        return ItemBuilder.from(Material.GREEN_STAINED_GLASS_PANE)
                .name(GuiText.component("gui.settings.player.change-hero.name"))
                .lore(GuiText.lore("gui.settings.player.change-hero.lore"))
                .modelData("gui", "player", "change-hero")
                .build();
    }

    private void handleChangeHero(SettingsGUI host, ClickType click) {
        if (click == ClickType.RIGHT) {
            gamePlayer.clearHeroOverride();
            host.playConfirm();
            host.refresh();
            return;
        }
        GUIManager.open(player, HeroSelectGUI.forOverride(
                player, targetName(gamePlayer.uuid()),
                gamePlayer::setHeroOverride, gamePlayer::clearHeroOverride, this));
    }

    private static PlayerSettings teamDisplayCopy(Game game, Team team) {
        for (UUID uuid : team.playerUUIDs()) {
            return new PlayerSettings(game.getGamePlayer(uuid).settings());
        }
        return new PlayerSettings(game.defaultPlayerSettings());
    }

    private static String targetName(UUID target) {
        Player p = Bukkit.getPlayer(target);
        return p != null ? p.getName() : GuiText.raw("gui.common.unknown-player");
    }
}
