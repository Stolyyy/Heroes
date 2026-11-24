package me.stolyy.heroes.vfx.ui.prompt;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/**
 * One-shot input facade for GUIs. Currently backed by {@link PromptListener} intercepting the
 * player's next chat message; swapping to sign or anvil input later only changes the listener.
 */
public final class PromptManager {

    record PendingPrompt(
            int minInt, int maxInt, IntConsumer onInt,
            double minDouble, double maxDouble, DoubleConsumer onDouble,
            Consumer<String> onText,
            Runnable onCancel) {}

    // Concurrent: PromptListener.onChat reads on the async chat thread to cancel broadcast.
    private static final Map<UUID, PendingPrompt> pending = new ConcurrentHashMap<>();

    private PromptManager() {}

    public static void initialize() {
        Bukkit.getPluginManager().registerEvents(new PromptListener(), Heroes.getInstance());
    }

    /** Prompts for an integer; out-of-range answers are clamped to {@code [min, max]}. */
    public static void promptInt(Player player, String message, int min, int max,
                                 IntConsumer onValue, Runnable onCancel) {
        GUIManager.close(player);
        player.sendMessage(Component.text(message, NamedTextColor.GOLD));
        player.sendMessage(GuiText.component("prompt.int-hint"));
        pending.put(player.getUniqueId(), new PendingPrompt(min, max, onValue, 0, 0, null, null, onCancel));
    }

    /** Prompts for a decimal value; out-of-range answers are clamped to {@code [min, max]}. */
    public static void promptDouble(Player player, String message, double min, double max,
                                    DoubleConsumer onValue, Runnable onCancel) {
        GUIManager.close(player);
        player.sendMessage(Component.text(message, NamedTextColor.GOLD));
        player.sendMessage(GuiText.component("prompt.double-hint"));
        pending.put(player.getUniqueId(), new PendingPrompt(0, 0, null, min, max, onValue, null, onCancel));
    }

    /** Prompts for free text. */
    public static void promptText(Player player, String message,
                                  Consumer<String> onValue, Runnable onCancel) {
        GUIManager.close(player);
        player.sendMessage(Component.text(message, NamedTextColor.GOLD));
        player.sendMessage(GuiText.component("prompt.text-hint"));
        pending.put(player.getUniqueId(), new PendingPrompt(0, 0, null, 0, 0, null, onValue, onCancel));
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
