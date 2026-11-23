package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.ui.gui.BugCharmsGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcConfig;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * NPC that displays a hero in the lobby for selection.
 * Clicking selects that hero for the player.
 */
public class HeroDisplayNpc extends Npc {

    private static final String ID_PREFIX = "hero_display_";

    private final String heroName;
    private final String displayName;
    private final NpcConfig.ArmorData armorData;

    /**
     * Creates a hero display NPC from config data.
     */
    public HeroDisplayNpc(NpcConfig.HeroNpcData data) {
        super(ID_PREFIX + data.id(), data.location());
        this.heroName = data.hero();
        this.displayName = data.name();
        this.armorData = data.armor();
    }

    /**
     * Creates a hero display NPC with explicit parameters.
     * @param npcId the unique NPC ID (without prefix)
     * @param heroName the internal hero name
     * @param displayName the formatted display name with color codes
     * @param location spawn location
     * @param armorData armor configuration (head model + color)
     */
    public HeroDisplayNpc(String npcId, String heroName, String displayName, Location location, NpcConfig.ArmorData armorData) {
        super(ID_PREFIX + npcId, location);
        this.heroName = heroName.toLowerCase();
        this.displayName = displayName;
        this.armorData = armorData;
    }

    /**
     * @return the hero name this NPC represents
     */
    public String getHeroName() {
        return heroName;
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);
        if (handle == null) return;

        // Set colored display name from config
        service.setDisplayName(handle, displayName);

        // Set equipment from config armor data
        applyArmorFromConfig(service);

        // Configure NPC behavior: out of the tab list, name visible overhead. (These used to be
        // the same underlying metadata; they are independent controls now.)
        service.setShowInTab(handle, false);
        service.setNameplateVisible(handle, true);
        service.setTurnToPlayer(handle, true);

        // Spawn the NPC
        service.spawn(handle);
    }

    /**
     * Applies armor from the config data.
     */
    private void applyArmorFromConfig(NpcService service) {
        if (armorData == null) return;

        ItemStack helmet = buildHelmet();
        ItemStack chest = buildColoredArmor(Material.LEATHER_CHESTPLATE);
        ItemStack legs = buildColoredArmor(Material.LEATHER_LEGGINGS);
        ItemStack boots = buildColoredArmor(Material.LEATHER_BOOTS);

        service.setEquipment(handle, helmet, chest, legs, boots, null);
    }

    /**
     * Builds the helmet from head config data.
     */
    private ItemStack buildHelmet() {
        if (armorData == null || armorData.head() == null) return null;

        NpcConfig.HeadData head = armorData.head();
        if (head.hero() == null || head.texture() == null || head.skin() == null) return null;

        return ItemBuilder.from(Material.SPONGE)
                .modelData(head.hero(), head.texture(), head.skin())
                .build();
    }

    /**
     * Builds colored leather armor.
     */
    private ItemStack buildColoredArmor(Material material) {
        if (armorData == null || armorData.color() == null) {
            return new ItemStack(material);
        }

        ItemStack item = new ItemStack(material);
        item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(armorData.color()));
        return item;
    }

    @Override
    public void handleInteraction(Player player) {
        // Special case: Bug hero opens charms GUI first
        if (heroName.equalsIgnoreCase("bug")) {
            GUIManager.open(player, new BugCharmsGUI(player));
            player.sendMessage(Component.text("Configure Bug's charms!", NamedTextColor.LIGHT_PURPLE));
        }

        // Select the hero
        HeroManager.selectHero(player, heroName);
    }

    /** Right-click: the hero info sheet (description, level, record, abilities, skins). */
    @Override
    public void handleSecondaryInteraction(Player player) {
        GUIManager.open(player, new me.stolyy.heroes.vfx.ui.gui.HeroInfoGUI(player, heroName, null));
    }

    /**
     * Creates the NPC ID for a hero name.
     */
    public static String createId(String heroName) {
        return ID_PREFIX + heroName.toLowerCase();
    }
}
