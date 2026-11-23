package me.stolyy.heroes.game;

import me.stolyy.heroes.game.settings.PlayerSettings;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-participant state for a single game: an independent {@link PlayerSettings} copy,
 * lives (max vs. current), kills, hero override, and lifecycle flags. Entries are created
 * when a player or bot enters the game and are never removed — leavers are marked via
 * {@link #markLeft()} so end-of-game stats (top killers etc.) survive the departure.
 */
public class GamePlayer {
    private final UUID uuid;
    private final PlayerSettings settings;
    private final GameBot bot; // non-null for bots

    private TeamColor team;
    private int maxLives;
    private int currentLives;
    private String heroOverride;
    private int kills;
    private int deaths;
    private final Map<String, Double> damageByAbility = new LinkedHashMap<>();
    private double damageTaken;
    private double voidDamageDealt;
    private double voidDamageTaken;
    private boolean respawning;
    private boolean leftGame;

    // The config key of the hero currently being played (set at start, updated on swap).
    private String currentHeroName;
    // Mode state (A4 design: mode-specific per-player state lives here, nullable when unused).
    // (Boss HP now writes settings().setHealth directly — settings are the single start-time
    // source of truth, so per-player GUI edits always win.)
    private String pendingHero;        // TDM: picked during the respawn countdown, applied at respawn
    private List<String> heroOrder;    // Evolution: per-player hero sequence
    private boolean evolutionComplete; // Evolution: got a kill on the final hero
    private int heroIndex;             // Evolution: position in heroOrder

    public GamePlayer(UUID uuid, TeamColor team, PlayerSettings settings, GameBot bot) {
        this.uuid = uuid;
        this.team = team;
        this.settings = settings;
        this.bot = bot;
        seedLives();
    }

    /** Re-seeds max and current lives from this participant's own settings (game start). */
    public void seedLives() {
        this.maxLives = settings.lives();
        this.currentLives = maxLives;
    }

    /**
     * The hero this participant plays: bots resolve via their {@link GameBot}; players use
     * the override if set, else a random pick when their settings say randomize, else their
     * persistent lobby selection. Override always wins over randomize.
     */
    public String resolveHeroName() {
        if (bot != null) return bot.getHeroName();
        if (heroOverride != null) return heroOverride;
        if (settings.randomizeHeroes()) {
            List<String> available = HeroConfig.getAvailableNames();
            if (!available.isEmpty()) {
                return available.get(ThreadLocalRandom.current().nextInt(available.size()));
            }
        }
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? HeroManager.getHeroName(p) : null;
    }

    public UUID uuid() { return uuid; }
    public PlayerSettings settings() { return settings; }
    public GameBot bot() { return bot; }
    public boolean isBot() { return bot != null; }

    public TeamColor team() { return team; }
    public void setTeam(TeamColor team) { this.team = team; }

    public int maxLives() { return maxLives; }
    public void setMaxLives(int maxLives) { this.maxLives = maxLives; }
    public int currentLives() { return currentLives; }
    public void setCurrentLives(int currentLives) { this.currentLives = currentLives; }
    public void subtractLife() { currentLives--; }

    public int kills() { return kills; }
    public void addKill() { kills++; }

    public int deaths() { return deaths; }
    public void addDeath() { deaths++; }

    public Map<String, Double> damageByAbility() { return damageByAbility; }
    public double damageDealt() { return damageByAbility.values().stream().mapToDouble(Double::doubleValue).sum(); }
    public void addDamageDealt(String ability, double amount) { damageByAbility.merge(ability, amount, Double::sum); }
    public double damageTaken() { return damageTaken; }
    public void addDamageTaken(double amount) { damageTaken += amount; }
    public double voidDamageDealt() { return voidDamageDealt; }
    public void addVoidDamageDealt(double amount) { voidDamageDealt += amount; }
    public double voidDamageTaken() { return voidDamageTaken; }
    public void addVoidDamageTaken(double amount) { voidDamageTaken += amount; }

    public String heroOverride() { return heroOverride; }
    public void setHeroOverride(String heroName) { this.heroOverride = heroName; }
    public void clearHeroOverride() { this.heroOverride = null; }

    public boolean evolutionComplete() { return evolutionComplete; }
    public void setEvolutionComplete(boolean evolutionComplete) { this.evolutionComplete = evolutionComplete; }

    public boolean respawning() { return respawning; }
    public void setRespawning(boolean respawning) { this.respawning = respawning; }

    // Tick-driven respawn countdown (decremented in Game.gameTick; paused while the
    // TDM re-pick GUI is open). 0 = no countdown running.
    private int respawnTicksLeft;
    public int respawnTicksLeft() { return respawnTicksLeft; }
    public void setRespawnTicksLeft(int respawnTicksLeft) { this.respawnTicksLeft = respawnTicksLeft; }

    public boolean leftGame() { return leftGame; }
    public void markLeft() { this.leftGame = true; }
    public void markRejoined() { this.leftGame = false; }

    public String currentHeroName() { return currentHeroName; }
    public void setCurrentHeroName(String currentHeroName) { this.currentHeroName = currentHeroName; }

    public void setPendingHero(String pendingHero) { this.pendingHero = pendingHero; }

    /** Returns and clears the respawn-window hero pick (null if none was made). */
    public String consumePendingHero() {
        String picked = pendingHero;
        pendingHero = null;
        return picked;
    }

    public List<String> heroOrder() { return heroOrder; }
    public void setHeroOrder(List<String> heroOrder) { this.heroOrder = heroOrder; }
    public int heroIndex() { return heroIndex; }
    public void setHeroIndex(int heroIndex) { this.heroIndex = heroIndex; }
}
