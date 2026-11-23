package me.stolyy.heroes.hero;

import me.stolyy.heroes.ability.kit.bug.CharmConfig;
import me.stolyy.heroes.ability.kit.bug.Charms;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.progression.PlayerStatsManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import me.stolyy.heroes.vfx.ui.visual.LobbyScoreboard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;

public class HeroManager {
    private static final Map<UUID, Hero> activeHeroes = new HashMap<>();
    private static final Map<UUID, Set<Charms>> equippedCharms = new HashMap<>();

    public static Set<me.stolyy.heroes.ability.kit.bug.Charms> getEquippedCharms(UUID uuid) {
        if (!equippedCharms.containsKey(uuid)) {
            // Load from persistent stats if not in memory
            Set<String> savedNames = PlayerStatsManager.getStats(uuid).equippedCharms();
            Set<Charms> loaded = new HashSet<>();
            for (String name : savedNames) {
                try {
                    loaded.add(Charms.valueOf(name));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!loaded.isEmpty()) {
                equippedCharms.put(uuid, loaded);
                return loaded;
            }
            return new HashSet<>();
        }
        return equippedCharms.get(uuid);
    }

    /** Bot-only: registers equipped charms in memory WITHOUT touching PlayerStats — an NPC uuid
     *  reaching getStats mints a ghost stats file (the R7 leak). getEquippedCharms hits this map
     *  first, so the stats fallback is never consulted for the bot. */
    public static void setEquippedCharmsTransient(UUID uuid, Set<Charms> charms) {
        equippedCharms.put(uuid, charms);
    }

    public static void setEquippedCharms(UUID uuid, Set<me.stolyy.heroes.ability.kit.bug.Charms> charms) {
        equippedCharms.put(uuid, charms);
        // Persist to player stats
        Set<String> charmNames = new HashSet<>();
        for (Charms charm : charms) {
            charmNames.add(charm.name());
        }
        PlayerStatsManager.getStats(uuid).setEquippedCharms(charmNames);
    }

    /**
     * Creates a new Hero instance for the given controller and registers it.
     */
    public static Hero createHero(String heroName, Controller controller) {
        // Load charm configuration on first use if needed
        CharmConfig.load();

        org.bukkit.configuration.file.YamlConfiguration config = HeroConfig.getConfig(heroName);
        Hero hero = HeroBuilder.buildHero(config);
        hero.setController(controller);
        hero.resetStats(controller);
        hero.applyAttributes(controller);
        activeHeroes.put(controller.getUniqueId(), hero);
        return hero;
    }

    /**
     * Registers an already-built Hero (used for minions, which are constructed via
     * {@link HeroBuilder#buildMinion} and wired up via {@code MinionHero.spawn} rather than
     * through {@link #createHero}). Subsequent {@link #getHero} lookups by entity UUID will
     * resolve to this hero instance.
     */
    public static void register(UUID uuid, Hero hero) {
        activeHeroes.put(uuid, hero);
    }

    /**
     * Removes a hero from the registry without invoking {@link Hero#cleanup()}. Use when the
     * caller is performing its own teardown (e.g. {@code MinionHero.despawn}).
     */
    public static void unregister(UUID uuid) {
        activeHeroes.remove(uuid);
    }

    public static Hero getHero(LivingEntity entity) {
        return activeHeroes.get(entity.getUniqueId());
    }

    public static Hero getHero(UUID uuid) {
        return activeHeroes.get(uuid);
    }

    /**
     * Gets the selected hero name for a player from their stats file.
     */
    public static String getHeroName(Player player) {
        return PlayerStatsManager.getStats(player).selectedHero();
    }

    public static void removePlayer(Player player) {
        Hero hero = activeHeroes.get(player.getUniqueId());
        if (hero != null) {
            hero.cleanup();
        }
        activeHeroes.remove(player.getUniqueId());
    }

    /**
     * Removes a hero by UUID only (for bots that have no Player object).
     */
    public static void removeHero(UUID uuid) {
        Hero hero = activeHeroes.get(uuid);
        if (hero != null) {
            hero.cleanup();
        }
        activeHeroes.remove(uuid);
    }

    public static void clear() {
        activeHeroes.clear();
        equippedCharms.clear();
    }

    /** Unlock check: default-unlocked in the YML or purchased/granted on the player's stats. */
    public static boolean isHeroUnlockedFor(Player player, String heroName) {
        return HeroConfig.isDefaultUnlocked(heroName)
                || PlayerStatsManager.getStats(player).isHeroUnlocked(heroName);
    }

    /**
     * Selects a hero for the player without creating an instance.
     * Just saves the selection to PlayerStats.
     */
    public static void selectHero(Player player, String heroName) {
        if (!HeroConfig.exists(heroName)) {
            player.sendMessage(Component.text("Hero not found: " + heroName, NamedTextColor.RED));
            return;
        }

        if (HeroConfig.isAdminOnly(heroName) && !player.isOp()) {
            player.sendMessage(Component.text("Hero not found: " + heroName, NamedTextColor.RED));
            return;
        }

        // The /hero command shares this path with the GUI, so the unlock gate lives here.
        if (!isHeroUnlockedFor(player, heroName)) {
            player.sendMessage(Component.text("You haven't unlocked " + HeroConfig.formatName(heroName)
                    + " yet!", NamedTextColor.RED));
            return;
        }

        PlayerStatsManager.getStats(player).setSelectedHero(heroName.toLowerCase());

        // Selection is pure preference — the live hero is built once in Game.start() from
        // resolveHeroName — so a selection made while attached to a game only takes effect next
        // game. Say so, and DON'T touch the scoreboard: LobbyScoreboard.apply would replace the
        // player's match sidebar with the lobby one until their next death rebuilt it.
        if (GameManager.isPlayerInGame(player)) {
            player.sendMessage(Component.text("Next game's hero: " + HeroConfig.formatName(heroName)
                    + " — your current match is unchanged.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Selected hero: " + HeroConfig.formatName(heroName), NamedTextColor.GREEN));
            LobbyScoreboard.apply(player);
        }
    }
}
