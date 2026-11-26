package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;

import java.util.List;

/** {@link BoardDisplay.Source} for the lobby ELO leaderboard (1v1 / 2v2). */
public final class EloBoardSource implements BoardDisplay.Source<GameMode> {
    @Override
    public String configSection() {
        return "leaderboard";
    }

    @Override
    public List<GameMode> views() {
        return List.of(GameMode.ONE_V_ONE, GameMode.TWO_V_TWO);
    }

    @Override
    public String title(String template, GameMode view) {
        return template.replace("{mode}", view.displayName());
    }

    @Override
    public List<PlayerStatsManager.LeaderboardEntry> top(GameMode view, int limit) {
        return PlayerStatsManager.getTopPlayers(view, limit);
    }

    @Override
    public Component value(PlayerStatsManager.LeaderboardEntry entry) {
        return GuiText.component("visual.board.elo-value", "value", entry.value());
    }

    @Override
    public Component viewLabel(GameMode view) {
        return GuiText.component("visual.board.elo-label", "mode", view.displayName());
    }
}
