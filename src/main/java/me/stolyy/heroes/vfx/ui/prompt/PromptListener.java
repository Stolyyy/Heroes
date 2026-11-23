package me.stolyy.heroes.vfx.ui.prompt;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.stolyy.heroes.Heroes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Chat backend for {@link PromptManager} — the swappable half of the A5 input facade. */
public class PromptListener implements Listener {

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        PromptManager.PendingPrompt prompt = PromptManager.get(player.getUniqueId());
        if (prompt == null) return;

        event.setCancelled(true); // prompt answers never broadcast
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Chat events are async — all game/GUI work hops to the main thread.
        Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> handleInput(player, prompt, input));
    }

    private void handleInput(Player player, PromptManager.PendingPrompt prompt, String input) {
        if (input.equalsIgnoreCase("cancel")) {
            PromptManager.clear(player.getUniqueId());
            player.sendMessage(Component.text("Cancelled.", NamedTextColor.GRAY));
            if (prompt.onCancel() != null) prompt.onCancel().run();
            return;
        }

        if (prompt.onText() != null) {
            PromptManager.clear(player.getUniqueId());
            prompt.onText().accept(input);
            return;
        }

        int value;
        try {
            value = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            // Expected-invalid user input: stay pending so the player can retry.
            player.sendMessage(Component.text("'" + input + "' is not a number — try again, or type 'cancel'.",
                    NamedTextColor.RED));
            return;
        }

        PromptManager.clear(player.getUniqueId());
        int clamped = Math.max(prompt.min(), Math.min(prompt.max(), value));
        if (clamped != value) {
            player.sendMessage(Component.text("Clamped to " + clamped + " (allowed: "
                    + prompt.min() + "-" + prompt.max() + ").", NamedTextColor.GRAY));
        }
        prompt.onInt().accept(clamped);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PromptManager.cancel(event.getPlayer());
    }
}
