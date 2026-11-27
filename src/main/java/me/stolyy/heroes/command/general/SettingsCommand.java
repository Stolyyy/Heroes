package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.settings.PreferencesGUI;
import org.bukkit.entity.Player;

import java.util.List;

/** /settings — opens the player's personal preferences menu. */
public class SettingsCommand extends HeroesCommand {

    public SettingsCommand() {
        super("settings", "Open your personal preferences", "/settings", List.of("prefs", "preferences"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        GUIManager.open(player, new PreferencesGUI(player, null));
    }
}
