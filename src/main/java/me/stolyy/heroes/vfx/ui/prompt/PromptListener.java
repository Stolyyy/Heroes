package me.stolyy.heroes.vfx.ui.prompt;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;

/** Chat backend for {@link PromptManager}. */
public class PromptListener implements Listener {

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PromptManager.PendingPrompt prompt = PromptManager.get(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Chat events fire off the main thread; GUI reopening and callbacks must run sync.
        Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> handleInput(player, prompt, input));
    }

    private void handleInput(Player player, PromptManager.PendingPrompt prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            PromptManager.clear(player.getUniqueId());
            player.sendMessage(GuiText.component("prompt.cancelled"));
            if (prompt.onCancel() != null) prompt.onCancel().run();
            return;
        }

        if (prompt.onText() != null) {
            PromptManager.clear(player.getUniqueId());
            prompt.onText().accept(input);
            return;
        }

        if (prompt.onDouble() != null) {
            double value;
            try {
                value = Double.parseDouble(input);
            } catch (NumberFormatException e) {
                player.sendMessage(GuiText.component("prompt.not-a-number", "input", input));
                return;
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                player.sendMessage(GuiText.component("prompt.not-a-number", "input", input));
                return;
            }
            PromptManager.clear(player.getUniqueId());
            double clamped = Math.max(prompt.minDouble(), Math.min(prompt.maxDouble(), value));
            if (clamped != value) {
                player.sendMessage(GuiText.component("prompt.clamped", Map.of(
                        "value", formatDouble(clamped),
                        "min", formatDouble(prompt.minDouble()),
                        "max", formatDouble(prompt.maxDouble()))));
            }
            prompt.onDouble().accept(clamped);
            return;
        }

        int value;
        try {
            value = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            player.sendMessage(GuiText.component("prompt.not-a-number", "input", input));
            return;
        }

        PromptManager.clear(player.getUniqueId());
        int clamped = Math.max(prompt.minInt(), Math.min(prompt.maxInt(), value));
        if (clamped != value) {
            player.sendMessage(GuiText.component("prompt.clamped", Map.of(
                    "value", String.valueOf(clamped),
                    "min", String.valueOf(prompt.minInt()),
                    "max", String.valueOf(prompt.maxInt()))));
        }
        prompt.onInt().accept(clamped);
    }

    private static String formatDouble(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PromptManager.cancel(event.getPlayer());
    }
}
