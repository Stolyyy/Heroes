package me.stolyy.heroes.npc;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

/** Routes Citizens NPC clicks to {@link NpcManager}. */
public class NpcInteractionListener implements Listener {

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        String npcId = extractNpcId(event.getNPC());
        if (npcId == null) return;
        Player player = event.getClicker();
        NpcManager.handleSecondaryInteraction(npcId, player);
    }

    @EventHandler
    public void onNpcLeftClick(NPCLeftClickEvent event) {
        String npcId = extractNpcId(event.getNPC());
        if (npcId == null) return;
        Player player = event.getClicker();
        NpcManager.handleInteraction(npcId, player);
    }

    private static @Nullable String extractNpcId(NPC npc) {
        return npc.data().get("heroes_npc_id");
    }
}
