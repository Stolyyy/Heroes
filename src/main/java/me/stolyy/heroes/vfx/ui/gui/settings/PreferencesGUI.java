package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.GUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** Personal player preferences menu (opened from the lobby menu or {@code /settings}). */
public class PreferencesGUI extends SettingsGUI {
    private static final int SIZE = 27;
    private static final int SLOT_CHAT_PINGS = 13;
    private static final int SLOT_BACK = 22;

    public PreferencesGUI(Player player, @Nullable GUI returnTo) {
        super(null, player, SIZE,
                GuiText.raw("gui.settings.preferences.title"),
                returnTo, SLOT_BACK, returnTo == null);

        Target<PlayerStats> target = new Target<>(
                () -> PlayerStatsManager.getStats(player),
                change -> change.apply(PlayerStatsManager.getStats(player)));

        addRow(Row.toggle(SLOT_CHAT_PINGS, target,
                Lens.mutable(PlayerStats::isChatPingsEnabled, PlayerStats::setChatPingsEnabled),
                "gui.settings.preferences.chat-pings",
                Material.BELL, Material.GRAY_DYE));
    }
}
