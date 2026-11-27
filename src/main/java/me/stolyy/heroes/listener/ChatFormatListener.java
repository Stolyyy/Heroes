package me.stolyy.heroes.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chat as {@code name: message} with per-viewer name colouring and {@code @username} pings
 * (highlighted and accompanied by a sound only for mentioned players who have pings enabled).
 */
public class ChatFormatListener implements Listener {
    private static final Pattern MENTION_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])");

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Set<UUID> pingedIds = findPingedViewers(plain, event.viewers());

        String highlightColor = null;
        if (!pingedIds.isEmpty()) {
            highlightColor = GameConfigManager.getChatPingHighlightColor();
            SoundEffect pingSound = GameConfigManager.getChatPingSound();
            Set<UUID> soundTargets = new HashSet<>(pingedIds);
            Bukkit.getScheduler().runTask(Heroes.getInstance(), () -> {
                for (UUID targetId : soundTargets) {
                    Player p = Bukkit.getPlayer(targetId);
                    if (p != null && p.isOnline()) pingSound.play(p);
                }
            });
        }

        String finalHighlight = highlightColor;
        event.renderer((source, displayName, message, viewer) -> {
            Component renderedMessage = message;
            if (finalHighlight != null && viewer instanceof Player vp && pingedIds.contains(vp.getUniqueId())) {
                Pattern ownMention = Pattern.compile(
                        "(?i)(?<![A-Za-z0-9_])@" + Pattern.quote(vp.getName()) + "(?![A-Za-z0-9_])");
                renderedMessage = message.replaceText(TextReplacementConfig.builder()
                        .match(ownMention)
                        .replacement((match, builder) ->
                                LegacyComponentSerializer.legacyAmpersand().deserialize(finalHighlight + match.group()))
                        .build());
            }
            return Component.text(source.getName(), nameColor(source, viewer))
                    .append(Component.text(": ", NamedTextColor.GRAY))
                    .append(renderedMessage);
        });
    }

    private static Set<UUID> findPingedViewers(String plain, Set<Audience> viewers) {
        Set<UUID> viewerIds = new HashSet<>();
        for (Audience audience : viewers) {
            if (audience instanceof Player p && Interactions.isRealPlayer(p)) {
                viewerIds.add(p.getUniqueId());
            }
        }
        Set<UUID> pinged = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(plain);
        while (matcher.find()) {
            Player target = Bukkit.getPlayerExact(matcher.group(1));
            if (target == null || !viewerIds.contains(target.getUniqueId())) continue;
            if (PlayerStatsManager.getStats(target.getUniqueId()).isChatPingsEnabled()) {
                pinged.add(target.getUniqueId());
            }
        }
        return pinged;
    }

    private static NamedTextColor nameColor(Player sender, Audience viewer) {
        Game game = GameManager.getPlayerGame(sender);
        if (game != null && viewer instanceof Player viewerPlayer
                && GameManager.getPlayerGame(viewerPlayer) == game) {
            return game.playerColor(sender.getUniqueId()).textColor();
        }
        if (PlayerStatsManager.getStats(sender.getUniqueId()).isSeasonChampion()) return NamedTextColor.GOLD;
        return game != null ? NamedTextColor.WHITE : NamedTextColor.GREEN;
    }
}
