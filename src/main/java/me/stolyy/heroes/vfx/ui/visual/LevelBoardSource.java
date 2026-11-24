package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** {@link BoardDisplay.Source} for the lobby level leaderboard (Overall + per non-admin hero). */
public final class LevelBoardSource implements BoardDisplay.Source<LevelBoardSource.View> {

    public record View(@Nullable String heroName) {
        public static final View OVERALL = new View(null);

        public String displayName() {
            return heroName == null
                    ? GuiText.raw("visual.board.level-overall")
                    : HeroConfig.formatName(heroName);
        }
    }

    @Override
    public String configSection() {
        return "level-leaderboard";
    }

    @Override
    public List<View> views() {
        List<View> views = new ArrayList<>();
        views.add(View.OVERALL);
        HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name))
                .sorted()
                .map(View::new)
                .forEach(views::add);
        return views;
    }

    @Override
    public String title(String template, View view) {
        return template.replace("{view}", view.displayName());
    }

    @Override
    public List<PlayerStatsManager.LeaderboardEntry> top(View view, int limit) {
        return PlayerStatsManager.getTopByLevel(view.heroName(), limit);
    }

    @Override
    public Component value(PlayerStatsManager.LeaderboardEntry entry) {
        return GuiText.component("visual.board.level-value", "value", entry.value());
    }

    @Override
    public Component viewLabel(View view) {
        return GuiText.component("visual.board.level-label", "view", view.displayName());
    }
}
