package me.stolyy.heroes.game.rules;

import me.stolyy.heroes.game.GamePlayer;

/**
 * What a death-with-no-respawn means for this mode: consulted by Game.handleDeath
 * after the life is subtracted to decide elimination vs respawn.
 */
public enum DeathOutcome {

    /** Classic: out of the game when the last life is spent. */
    LIVES_BASED {
        @Override
        public boolean shouldEliminate(GamePlayer gamePlayer) {
            return gamePlayer.currentLives() <= 0;
        }
    },

    /** No eliminations, infinite respawns (TDM, Evolution). */
    ALWAYS_RESPAWN {
        @Override
        public boolean shouldEliminate(GamePlayer gamePlayer) {
            return false;
        }

        @Override
        public boolean consumesLife() {
            return false;
        }
    };

    public abstract boolean shouldEliminate(GamePlayer gamePlayer);

    /** Whether a death decrements currentLives. False keeps the sidebar sane in endless modes. */
    public boolean consumesLife() {
        return true;
    }
}
