package me.stolyy.heroes.game.queue;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.RestartManager;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Opt-in automatic requeueing: armed with {@code /autoqueue <mode>}, disarmed with
 * {@code /autoqueue stop} or by leaving the queue. While armed, the player is dropped back into
 * the queue a few seconds after every time they land in the lobby.
 *
 * <p>The lobby return is <em>observed</em>, not hooked. {@link #tick()} runs once a second off
 * {@link QueueManager}'s title task and counts down whenever the player is in a state where
 * {@link QueueManager#joinQueue} would accept them — out of a game entirely, or spectating the
 * game they armed from. That is the same condition the join itself is gated on, so the countdown
 * can never run up to a join that was doomed from the start, and it needs no cooperation from
 * {@code Game}'s teardown path (which reaches the lobby by three different routes).
 *
 * <p>The queued mode comes from the command argument, never from the last game played: arming is
 * an explicit statement of what you want next.
 */
public final class AutoQueue {
    private AutoQueue() {}

    /** Matches QueueManager.updateTitles — a 2s stay refreshed every 1s never flickers. */
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO);

    private static final Map<UUID, Armed> armed = new HashMap<>();

    private static final class Armed {
        final GameMode mode;
        final int countdown;
        final int soundSeconds;
        final String sound;
        final float soundVolume;
        final float soundPitch;
        /**
         * The game the player was spectating when they armed, or null. Only that one game keeps
         * the countdown running while attached to a game — see {@link #eligible}.
         */
        Game spectatorAnchor;
        /** Seconds still to show, or -1 while the countdown is suspended. */
        int secondsLeft = -1;

        Armed(GameMode mode, Game spectatorAnchor) {
            this.mode = mode;
            this.spectatorAnchor = spectatorAnchor;

            // Read at arm time, not per tick: a bad yml then fails inside the command the player
            // just typed rather than once a second inside a repeating task.
            ConfigurationSection config = Configs.requireSection(
                    GameConfigManager.getGameConfig(), "queue.auto-requeue");
            this.countdown = Configs.requireInt(config, "countdown-seconds");
            this.soundSeconds = Configs.requireInt(config, "tick-sound-seconds");
            this.sound = Configs.requireString(config, "tick-sound");
            this.soundVolume = (float) Configs.requireDouble(config, "tick-sound-volume");
            this.soundPitch = (float) Configs.requireDouble(config, "tick-sound-pitch");
        }
    }

    public static void arm(Player player, GameMode mode) {
        if (RestartManager.isDraining()) {
            player.sendMessage(GameConfigManager.getMessage("restart.queue-closed"));
            return;
        }

        // Spectators may arm mid-match and stay watching until the queue pops, which is what
        // joinQueue already allows for a hand-typed /join.
        Game anchor = GameManager.isSpectating(player) ? GameManager.getPlayerGame(player) : null;
        armed.put(player.getUniqueId(), new Armed(mode, anchor));
        player.sendMessage(GameConfigManager.getMessage("queue.auto.enabled",
                Map.of("mode", mode.displayName())));
    }

    /** Disarms and reports it to the player. Returns false (and says nothing) if not armed. */
    public static boolean stop(Player player) {
        Armed state = armed.remove(player.getUniqueId());
        if (state == null) return false;
        for (Player viewer : audience(player)) viewer.clearTitle();
        player.sendMessage(GameConfigManager.getMessage("queue.auto.disabled"));
        return true;
    }

    public static boolean isArmed(Player player) {
        return armed.containsKey(player.getUniqueId());
    }

    /** The mode this player is armed for, or null. */
    public static GameMode armedMode(Player player) {
        Armed state = armed.get(player.getUniqueId());
        return state == null ? null : state.mode;
    }

    public static void clear() {
        armed.clear();
    }

    /** Silent disarm of every loop (restart drain); returns who was armed so the caller can tell them. */
    static Set<UUID> disarmAll() {
        Set<UUID> ids = new HashSet<>(armed.keySet());
        armed.clear();
        return ids;
    }

    /** Everyone with an armed loop, queued or not. QueueManager's AFK sweep runs over this too. */
    static Set<UUID> armedPlayers() {
        return new HashSet<>(armed.keySet());
    }

    static void tick() {
        if (armed.isEmpty()) return;

        Iterator<Map.Entry<UUID, Armed>> iterator = armed.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Armed> mapEntry = iterator.next();
            Player player = Bukkit.getPlayer(mapEntry.getKey());
            if (player == null || !player.isOnline()) {
                iterator.remove();
                continue;
            }

            Armed state = mapEntry.getValue();

            // Already queued — either this fired and we are waiting for a match, or they queued by
            // hand. Either way the queue title owns the screen from here.
            if (QueueManager.isQueued(player) || !eligible(player, state)) {
                state.secondsLeft = -1;
                continue;
            }

            Set<Player> audience = audience(player);
            if (audience.stream().anyMatch(GUIManager::hasOpenGui)) {
                // Stall, do not reset: someone in the party is picking a hero or reading a menu,
                // and yanking them into a match mid-decision is the whole thing this avoids.
                showTitle(player, audience, "queue.auto.paused-title", "queue.auto.paused-subtitle",
                        state, -1);
                continue;
            }

            if (state.secondsLeft < 0) state.secondsLeft = state.countdown;

            if (state.secondsLeft > 0) {
                showTitle(player, audience, "queue.auto.title", "queue.auto.subtitle",
                        state, state.secondsLeft);
                if (state.secondsLeft <= state.soundSeconds) playTick(audience, state);
                state.secondsLeft--;
                continue;
            }

            // Suspend before joining so this player is never a countdown title and a queue title in
            // the same tick — QueueManager.updateTitles runs immediately after us.
            state.secondsLeft = -1;
            for (Player viewer : audience) viewer.clearTitle();

            Game before = GameManager.getPlayerGame(player);
            QueueManager.joinQueue(player, state.mode);
            // An instantly-popped match dequeues the player again in the same call, so "did it
            // work" is "queued OR now in a different game", not just "queued".
            boolean joined = QueueManager.isQueuedFor(player, state.mode)
                    || GameManager.getPlayerGame(player) != before;
            if (!joined) {
                // joinQueue already said why it refused; this only says the loop is over.
                iterator.remove();
                player.sendMessage(GameConfigManager.getMessage("queue.auto.stopped-error",
                        Map.of("mode", state.mode.displayName())));
            }
        }
    }

    /**
     * Whether the countdown may run. Out of a game entirely is the normal case. Attached to a game
     * only counts when it is the very game they armed from as a spectator — in particular a player
     * eliminated into their own match's spectator team does NOT resume counting, so auto-queue
     * never pulls someone out of a match their team is still playing.
     */
    private static boolean eligible(Player player, Armed state) {
        Game game = GameManager.getPlayerGame(player);
        if (game == null) {
            state.spectatorAnchor = null;
            return true;
        }
        if (state.spectatorAnchor != game) {
            state.spectatorAnchor = null;
            return false;
        }
        return GameManager.isSpectating(player);
    }

    /** The armed player plus their online party members — everyone the countdown will affect. */
    private static Set<Player> audience(Player player) {
        Set<Player> members = PartyManager.getPartyMembers(player);
        if (members.isEmpty()) return Set.of(player);
        Set<Player> audience = new HashSet<>(members);
        audience.add(player);
        return audience;
    }

    private static void showTitle(Player owner, Set<Player> audience, String titleKey,
                                  String subtitleKey, Armed state, int seconds) {
        Component title = GameConfigManager.getMessage(titleKey,
                Map.of("seconds", String.valueOf(seconds), "mode", state.mode.displayName()));
        Component ownSubtitle = GameConfigManager.getMessage(subtitleKey,
                Map.of("mode", state.mode.displayName()));
        Component partySubtitle = GameConfigManager.getMessage(subtitleKey + "-party",
                Map.of("mode", state.mode.displayName(), "player", owner.getName()));
        for (Player viewer : audience) {
            viewer.showTitle(Title.title(title,
                    viewer.equals(owner) ? ownSubtitle : partySubtitle, TITLE_TIMES));
        }
    }

    private static void playTick(Set<Player> audience, Armed state) {
        for (Player viewer : audience) {
            SoundBuilder.of(state.sound).volume(state.soundVolume).pitch(state.soundPitch).play(viewer);
        }
    }
}
