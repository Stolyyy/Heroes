package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcConfig;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.vfx.effect.DisplayTags;
import me.stolyy.heroes.vfx.effect.TextDisplayBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Lobby queue NPC displaying mode label, active-player count, and waiting/queue count above its head.
 */
public class QueueNpc extends Npc {

    private static final double TEXT_BASE_HEIGHT = 2.3;
    private static final double TEXT_SPACING = 0.3;

    private final GameMode mode;
    private final String displayName;
    private final String skin;
    private final List<TextDisplay> lines = new ArrayList<>();

    public QueueNpc(NpcConfig.QueueNpcData data) {
        super("queue_" + data.mode().name().toLowerCase(), data.location());
        this.mode = data.mode();
        this.displayName = data.name();
        this.skin = data.skin();
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);
        if (handle == null) return;

        if (skin != null && !skin.isEmpty()) {
            service.setSkin(handle, skin, null);
        }
        service.setShowInTab(handle, false);
        service.setNameplateVisible(handle, false);
        service.setTurnToPlayer(handle, true);
        service.spawn(handle);

        for (int i = 0; i < 3; i++) {
            double height = TEXT_BASE_HEIGHT + TEXT_SPACING * (2 - i);
            Location lineLoc = location.clone().add(0, height, 0);
            lines.add(TextDisplayBuilder.create(lineLoc)
                    .text(Component.empty())
                    .billboard(Display.Billboard.CENTER)
                    .transparentBackground()
                    .tag(DisplayTags.QUEUE_NPC_TEXT)
                    .spawn());
        }
        lines.get(0).text(MiniMessage.miniMessage().deserialize(displayName));
        updateText();
    }

    /** Rewrites the active and waiting player counts above this NPC. */
    public void updateText() {
        if (lines.size() < 3 || !lines.get(1).isValid()) return;

        boolean includeSpectators = false;
        int playing = 0;
        int customWaiting = 0;
        for (Game game : GameManager.getActiveGames()) {
            if (game.gameMode() != mode) continue;
            if (game.gameState() == GameState.IN_PROGRESS || game.gameState() == GameState.STARTING) {
                playing += game.onlinePlayers(includeSpectators).size();
            } else if (game.gameState() == GameState.WAITING && mode == GameMode.CUSTOM) {
                customWaiting += game.onlinePlayers().size();
            }
        }
        int waiting = mode == GameMode.CUSTOM ? customWaiting : QueueManager.queueSize(mode);

        lines.get(1).text(Component.text("Playing: ", NamedTextColor.GRAY)
                .append(Component.text(playing, NamedTextColor.WHITE)));
        lines.get(2).text(Component.text(mode == GameMode.CUSTOM ? "Waiting: " : "In Queue: ", NamedTextColor.GRAY)
                .append(Component.text(waiting, NamedTextColor.WHITE)));
    }

    @Override
    public void handleInteraction(Player player) {
        if (mode == GameMode.CUSTOM) {
            GameManager.joinGame(player, GameMode.CUSTOM, null);
            return;
        }
        if (QueueManager.isQueuedFor(player, mode)) {
            QueueManager.leaveQueue(player, mode);
        } else {
            QueueManager.joinQueue(player, mode);
        }
    }

    @Override
    public void despawn() {
        for (TextDisplay line : lines) {
            if (line.isValid()) line.remove();
        }
        lines.clear();
        super.despawn();
    }
}
