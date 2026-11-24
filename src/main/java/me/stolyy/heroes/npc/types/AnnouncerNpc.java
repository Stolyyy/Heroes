package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcConfig;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.LobbyMenuGUI;
import org.bukkit.entity.Player;

/** Main lobby menu NPC opening {@link LobbyMenuGUI} on interaction. */
public class AnnouncerNpc extends Npc {

    public static final String NPC_ID = "announcer";

    private final String displayName;
    private final String skinUsername;

    public AnnouncerNpc(NpcConfig.AnnouncerData data) {
        super(NPC_ID, data.location());
        this.displayName = data.name();
        this.skinUsername = data.skin();
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);
        if (handle == null) return;

        service.setDisplayName(handle, displayName);
        service.setSkin(handle, skinUsername, null);
        service.setShowInTab(handle, false);
        service.setNameplateVisible(handle, true);
        service.setTurnToPlayer(handle, true);
        service.spawn(handle);
    }

    @Override
    public void handleInteraction(Player player) {
        GUIManager.open(player, new LobbyMenuGUI(player));
    }
}
