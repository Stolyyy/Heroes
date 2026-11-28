package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.PracticeManager;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Applies the default hero speed boost and single mid-air double jump to lobby players,
 * reading live parameters from {@code heroes/default.yml}.
 */
public final class LobbyMobility {
    private static final double VANILLA_MOVEMENT_SPEED = 0.1;
    // Brief cooldown before ground-contact checks re-arm flight so client ground-lag right after
    // taking off from a standing double-tap does not immediately grant a second mid-air jump.
    private static final int LAND_REARM_DELAY_TICKS = 4;

    private static final Map<UUID, Integer> lastJumpTick = new HashMap<>();

    private LobbyMobility() {}

    private static YamlConfiguration defaultConfig() {
        YamlConfiguration config = HeroConfig.getConfig("default");
        if (config == null) {
            throw new IllegalStateException("heroes/default.yml is not loaded");
        }
        return config;
    }

    /** True when {@code player} is free in the lobby (not in a game, practice, or non-ADVENTURE mode). */
    public static boolean isInLobby(Player player) {
        return player.getGameMode() == GameMode.ADVENTURE
                && HeroManager.getHero(player) == null
                && !GameManager.isPlayerInGame(player)
                && !PracticeManager.isPracticing(player);
    }

    /** Applies lobby speed and arms the single mid-air double jump. */
    public static void apply(Player player) {
        lastJumpTick.remove(player.getUniqueId());
        double speed = Configs.requireDouble(defaultConfig(), "stats.speed");
        AttributeInstance speedAttr = Objects.requireNonNull(player.getAttribute(Attribute.MOVEMENT_SPEED));
        speedAttr.setBaseValue(speed);
        player.setFlying(false);
        player.setAllowFlight(true);
    }

    /** Restores vanilla movement speed and strips lobby double-jump flight permission. */
    public static void reset(Player player) {
        lastJumpTick.remove(player.getUniqueId());
        AttributeInstance speedAttr = Objects.requireNonNull(player.getAttribute(Attribute.MOVEMENT_SPEED));
        speedAttr.setBaseValue(VANILLA_MOVEMENT_SPEED);
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /** Fires the single lobby double jump if armed; returns false if already used in this airtime. */
    public static boolean performJump(Player player) {
        if (lastJumpTick.containsKey(player.getUniqueId())) return false;

        YamlConfiguration config = defaultConfig();
        double xStrength = Configs.requireDouble(config, "mobility.x_strength");
        double yStrength = Configs.requireDouble(config, "mobility.y_strength");
        SoundEffect sound = new SoundEffect(Configs.requireSection(config, "mobility.sound"));
        VisualEffect effect = new VisualEffect(Configs.requireSection(config, "mobility.effect"));

        lastJumpTick.put(player.getUniqueId(), Bukkit.getCurrentTick());
        Vector velocity = player.getLocation().getDirection().multiply(xStrength);
        velocity.setY(yStrength);
        player.setVelocity(velocity);
        sound.play(player.getLocation(), player);
        effect.play(player.getLocation());
        player.setAllowFlight(false);
        return true;
    }

    /** Re-arms lobby double jump once the player touches down after taking off. */
    public static void onLand(Player player) {
        Integer jumpedAt = lastJumpTick.get(player.getUniqueId());
        if (jumpedAt != null) {
            if (Bukkit.getCurrentTick() - jumpedAt < LAND_REARM_DELAY_TICKS) return;
            lastJumpTick.remove(player.getUniqueId());
        }
        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }
    }

    public static void clear() {
        lastJumpTick.clear();
    }
}
