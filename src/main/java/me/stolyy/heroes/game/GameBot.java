package me.stolyy.heroes.game;

import me.stolyy.heroes.npc.NpcHandle;

/**
 * Lightweight data class representing a bot in the game system.
 * The NPC is created before the game starts (when the bot is added in the GUI),
 * so we have a LivingEntity and UUID available immediately.
 */
public class GameBot {

    private final String name;
    private String heroName;
    private final NpcHandle npcHandle;
    // Difficulty tier 1-5, cycled in BotSettingsGUI; maps to an input delay via bots.yml,
    // overriding the hero yml's ai.input_delay when the AI is built at game start.
    private int difficultyTier = 3;

    public GameBot(String name, String heroName, NpcHandle npcHandle) {
        this.name = name;
        this.heroName = heroName;
        this.npcHandle = npcHandle;
    }

    public String getName() { return name; }
    public String getHeroName() { return heroName; }
    public void setHeroName(String heroName) { this.heroName = heroName; }
    public NpcHandle getNpcHandle() { return npcHandle; }
    public int getDifficultyTier() { return difficultyTier; }
    public void setDifficultyTier(int difficultyTier) { this.difficultyTier = difficultyTier; }
}
