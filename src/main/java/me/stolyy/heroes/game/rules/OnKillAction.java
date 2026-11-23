package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GamePlayer;

import java.util.List;

/**
 * Hook fired in Game.handleDeath right after kill credit, whenever the killer resolves to a
 * GamePlayer. A minion has no GamePlayer of its own, so it arrives here as its OWNER
 * (Game.creditedId) — a summon's kill advances the summoner.
 */
public enum OnKillAction {

    NONE {
        @Override
        public void apply(GamePlayer killer, GamePlayer victim, Game game) {
        }
    },

    /**
     * Evolution: the killer advances to the next hero in their per-player order
     * (initialized in Game.start()). Climbing past the FINAL hero is the ONLY way to win —
     * whose ladder has to finish is the two evolution toggles' business, see {@link #teamWon}.
     *
     * <p>There used to be a second end here, on the team's combined kill count. It made a 3-player
     * team win at 10 shared kills while its best player was 7 heroes up a 10-hero ladder, and it
     * bypassed both toggles on the way. The mode's "kill limit" is now the LADDER LENGTH instead
     * (applied in Game.initHeroOrder), which is what the sidebar has always displayed.
     */
    ADVANCE_HERO {
        @Override
        public void apply(GamePlayer killer, GamePlayer victim, Game game) {
            if (game.modeConfig().evolutionTeamAdvance()) {
                // A kill advances every (non-left) teammate through their own order.
                for (GamePlayer teammate : game.getGamePlayers().values()) {
                    if (teammate.leftGame() || teammate.heroOrder() == null) continue;
                    if (teammate.team() != killer.team()) continue;
                    advance(teammate, game);
                }
            } else {
                advance(killer, game);
            }

            if (teamWon(killer, game)) {
                game.endWithWinner(game.playerTeam(killer.uuid()));
            }
        }
    };

    /** Advance one player a step; reaching past the final hero marks them fully evolved. */
    private static void advance(GamePlayer gamePlayer, Game game) {
        if (gamePlayer.evolutionComplete()) return;
        List<String> order = gamePlayer.heroOrder();
        int next = gamePlayer.heroIndex() + 1;
        if (next >= order.size()) {
            gamePlayer.setEvolutionComplete(true);
            return;
        }
        gamePlayer.setHeroIndex(next);
        game.swapHero(gamePlayer, order.get(next));
    }

    /**
     * Win scope: by default the first fully evolved player wins it for their team;
     * with evolutionWinRequiresAll, EVERY (non-left) member of the team must be done.
     */
    private static boolean teamWon(GamePlayer killer, Game game) {
        boolean requireAll = game.modeConfig().evolutionWinRequiresAll();
        boolean any = false, all = true;
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (gamePlayer.leftGame() || gamePlayer.heroOrder() == null) continue;
            if (gamePlayer.team() != killer.team()) continue;
            if (gamePlayer.evolutionComplete()) any = true;
            else all = false;
        }
        return requireAll ? (any && all) : any;
    }

    public abstract void apply(GamePlayer killer, GamePlayer victim, Game game);
}
