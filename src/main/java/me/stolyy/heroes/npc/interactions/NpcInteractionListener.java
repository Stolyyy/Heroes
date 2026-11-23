package me.stolyy.heroes.npc.interactions;

import me.stolyy.heroes.npc.NpcManager;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listener for Citizens NPC interaction events. Delegates to NpcManager: right-clicks are
 * the "secondary" interaction (hero NPCs open the info sheet, others fall through to the
 * primary action), left-clicks are the primary interaction (hero NPCs quick-select).
 */
public class NpcInteractionListener implements Listener {

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        String npcId = event.getNPC().data().get("heroes_npc_id");
        if (npcId == null) return;
        Player player = event.getClicker();

        NpcManager.handleSecondaryInteraction(npcId, player);
    }

    @EventHandler
    public void onNpcLeftClick(NPCLeftClickEvent event) {
        String npcId = event.getNPC().data().get("heroes_npc_id");
        if (npcId == null) return;
        Player player = event.getClicker();

        NpcManager.handleInteraction(npcId, player);
    }
}
