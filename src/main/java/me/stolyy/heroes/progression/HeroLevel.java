package me.stolyy.heroes.progression;

import java.util.ArrayList;
import java.util.List;

/**
 * A hero's GLOBAL level/XP pool — one per hero per player, fed by every game mode
 * (1v1, 2v2, customs all pay into the same pool). Per-mode wins/losses/deaths stay
 * on {@link HeroProgress}.
 */
public class HeroLevel {
    private int level;
    private int xp; // XP into the current level (the level-up requirement is subtracted)

    /**
     * Grants XP and returns every level crossed by this grant (empty = no level-up),
     * so the caller can fire token grants and master-skin unlocks per level.
     */
    public List<Integer> addXp(int amount) {
        xp += amount;
        return checkLevelUp();
    }

    private List<Integer> checkLevelUp() {
        List<Integer> newLevels = new ArrayList<>();
        int xpRequired = ProgressionConfig.getXpForLevel(level);
        while (xp >= xpRequired) {
            xp -= xpRequired;
            level++;
            newLevels.add(level);
            xpRequired = ProgressionConfig.getXpForLevel(level);
        }
        return newLevels;
    }

    public int level() { return level; }
    public int xp() { return xp; }
    public void setLevel(int level) { this.level = level; }
    public void setXp(int xp) { this.xp = xp; }
}
