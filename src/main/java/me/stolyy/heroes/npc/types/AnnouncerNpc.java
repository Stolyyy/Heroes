package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.vfx.ui.gui.AnnouncerGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcConfig;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * NPC that serves as the main lobby announcer/menu NPC.
 * Clicking opens the AnnouncerGUI (main lobby menu).
 */
public class AnnouncerNpc extends Npc {

    public static final String NPC_ID = "announcer";

    private final String displayName;
    private final String skinUsername;

    /**
     * Creates an announcer NPC from config data.
     */
    public AnnouncerNpc(NpcConfig.AnnouncerData data) {
        super(NPC_ID, data.location());
        this.displayName = data.name();
        this.skinUsername = data.skin();
    }

    /**
     * Creates an announcer NPC with explicit parameters.
     * @param location spawn location
     * @param displayName the formatted display name with color codes
     * @param skinUsername Minecraft username to use for skin
     */
    public AnnouncerNpc(Location location, String displayName, String skinUsername) {
        super(NPC_ID, location);
        this.displayName = displayName;
        this.skinUsername = skinUsername != null ? skinUsername : "MHF_Steve";
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);
        if (handle == null) return;

        // Set colored display name
        service.setDisplayName(handle, displayName);

        // Set skin
        service.setSkin(handle, skinUsername, null);

        // Set equipment - give the announcer a book
        ItemStack book = new ItemStack(Material.BOOK);
        service.setEquipment(handle, null, null, null, null, book);

        // Out of the tab list, name visible overhead — the same pairing the hero display NPCs use.
        // The nameplate was hard-disabled here, which is why the announcer stood nameless even
        // though setDisplayName above hands Citizens the lobby.yml name. Citizens renders that
        // gradient name as its own hologram at bbHeight + 0.2 once NAMEPLATE_VISIBLE is on.
        service.setShowInTab(handle, false);
        service.setNameplateVisible(handle, true);
        service.setTurnToPlayer(handle, true);

        // Spawn the NPC
        service.spawn(handle);
    }

    @Override
    public void handleInteraction(Player player) {
        GUIManager.open(player, new AnnouncerGUI(player));
    }
}
