package me.stolyy.heroes.vfx.ui.prompt;

import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * One-shot input facade for GUIs (A5). Call sites never know the input mechanism — the
 * current implementation intercepts the player's next chat message ({@link PromptListener});
 * swapping to a sign/anvil input later only changes the listener internals.
 *
 * <p>Flow: {@code prompt*} closes the player's open GUI and registers a pending prompt; the
 * next chat message is consumed (never broadcast) and parsed on the main thread. Invalid
 * numeric input re-prompts; typing "cancel" aborts via the onCancel callback (typically
 * "reopen the GUI I came from").
 */
public final class PromptManager {

    record PendingPrompt(int min, int max, IntConsumer onInt, Consumer<String> onText, Runnable onCancel) {}

    private static final Map<UUID, PendingPrompt> pending = new HashMap<>();

    private PromptManager() {}

    /** Prompts for an integer; out-of-range answers are clamped to [min, max]. */
    public static void promptInt(Player player, String message, int min, int max,
                                 IntConsumer onValue, Runnable onCancel) {
        GUIManager.close(player);
        player.sendMessage(Component.text(message, NamedTextColor.GOLD));
        player.sendMessage(Component.text("Type a number in chat, or 'cancel' to go back.", NamedTextColor.GRAY));
        pending.put(player.getUniqueId(), new PendingPrompt(min, max, onValue, null, onCancel));
    }

    /** Prompts for free text. */
    public static void promptText(Player player, String message,
                                  Consumer<String> onValue, Runnable onCancel) {
        GUIManager.close(player);
        player.sendMessage(Component.text(message, NamedTextColor.GOLD));
        player.sendMessage(Component.text("Type your answer in chat, or 'cancel' to go back.", NamedTextColor.GRAY));
        pending.put(player.getUniqueId(), new PendingPrompt(0, 0, null, onValue, onCancel));
    }

    /** Aborts any pending prompt without firing callbacks (e.g. on quit). */
    public static void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    static PendingPrompt get(UUID uuid) {
        return pending.get(uuid);
    }

    static void clear(UUID uuid) {
        pending.remove(uuid);
    }
}
