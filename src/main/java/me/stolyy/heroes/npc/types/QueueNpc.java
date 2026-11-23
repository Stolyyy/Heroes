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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Lobby queue NPC — one per joinable mode. Three text layers hover above it (top to bottom):
 * the gamemode, players currently playing that mode, and players waiting (the ranked queue for
 * 1v1/2v2; WAITING custom lobbies for custom). The counts refresh once a second via
 * {@code NpcManager}'s update task.
 *
 * <p>Clicking is a toggle for queue modes — join the queue, click again to leave. Custom routes
 * through the normal custom-join flow instead (joining a waiting game / the map GUI), and leaving
 * a custom lobby stays the explicit /leave gesture.
 */
public class QueueNpc extends Npc {

    private static final double TEXT_BASE_HEIGHT = 2.3;
    private static final double TEXT_SPACING = 0.3;

    /**
     * Marks these lines as plugin-owned so the lobby leaderboards' orphan sweeps spare them. The
     * level board's sweep box used to reach the CUSTOM NPC (it stands inside it; 1v1 and 2v2 sit
     * just outside) and deleted its whole stack a tick after spawn, leaving a nameless Steve.
     */
    private static final NamespacedKey ENTITY_TAG = new NamespacedKey("heroes", "queue_npc_text");

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
        // The text stack carries all the info — the raw npc id must not float under it.
        service.setShowInTab(handle, false);
        service.setNameplateVisible(handle, false);
        service.setTurnToPlayer(handle, true);
        service.spawn(handle);

        // Three layers, top to bottom: mode / playing / waiting.
        for (int i = 0; i < 3; i++) {
            double height = TEXT_BASE_HEIGHT + TEXT_SPACING * (2 - i);
            Location lineLoc = location.clone().add(0, height, 0);
            TextDisplay line = location.getWorld().spawn(lineLoc, TextDisplay.class, d -> {
                d.text(Component.empty());
                d.setBillboard(Display.Billboard.CENTER);
                d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                d.setPersistent(false);
                d.getPersistentDataContainer().set(ENTITY_TAG, PersistentDataType.BYTE, (byte) 1);
            });
            lines.add(line);
        }
        lines.get(0).text(MiniMessage.miniMessage().deserialize(displayName));
        updateText();
    }

    /** Rewrites the two count lines. Called once a second from NpcManager's update task. */
    public void updateText() {
        if (lines.size() < 3 || !lines.get(1).isValid()) return;

        int playing = 0;
        int waiting = 0;
        for (Game game : GameManager.getActiveGames()) {
            if (game.gameMode() != mode) continue;
            if (game.gameState() == GameState.IN_PROGRESS || game.gameState() == GameState.STARTING) {
                playing += game.onlinePlayers(false).size();
            } else if (game.gameState() == GameState.WAITING) {
                waiting += game.onlinePlayers().size();
            }
        }
        if (mode != GameMode.CUSTOM) {
            waiting = QueueManager.queueSize(mode);
        }

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
        // Toggle: in the queue → out; out → in.
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
