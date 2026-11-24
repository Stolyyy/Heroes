package me.stolyy.heroes.npc.types;

import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.npc.Npc;
import me.stolyy.heroes.npc.NpcConfig;
import me.stolyy.heroes.npc.NpcService;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.gui.BugCharmsGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroInfoGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.Locale;

/** Lobby display NPC that selects its hero on left-click and opens {@link HeroInfoGUI} on right-click. */
public class HeroDisplayNpc extends Npc {

    private static final String ID_PREFIX = "hero_display_";

    private final String heroName;
    private final String displayName;
    private final NpcConfig.ArmorData armorData;

    public HeroDisplayNpc(NpcConfig.HeroNpcData data) {
        super(ID_PREFIX + data.id(), data.location());
        this.heroName = data.hero().toLowerCase(Locale.ROOT);
        this.displayName = data.name();
        this.armorData = data.armor();
    }

    public String getHeroName() {
        return heroName;
    }

    @Override
    public void spawn() {
        NpcService service = NpcServiceProvider.get();
        if (!service.isAvailable()) return;

        handle = service.create(id, location);
        if (handle == null) return;

        service.setDisplayName(handle, displayName);
        applyArmorFromConfig(service);

        service.setShowInTab(handle, false);
        service.setNameplateVisible(handle, true);
        service.setTurnToPlayer(handle, true);
        service.spawn(handle);
    }

    private void applyArmorFromConfig(NpcService service) {
        if (armorData == null) return;

        ItemStack helmet = buildHelmet();
        ItemStack chest = buildColoredArmor(Material.LEATHER_CHESTPLATE);
        ItemStack legs = buildColoredArmor(Material.LEATHER_LEGGINGS);
        ItemStack boots = buildColoredArmor(Material.LEATHER_BOOTS);

        service.setEquipment(handle, helmet, chest, legs, boots, null);
    }

    private ItemStack buildHelmet() {
        if (armorData == null || armorData.head() == null) return null;

        NpcConfig.HeadData head = armorData.head();
        if (head.hero() == null || head.texture() == null || head.skin() == null) {
            throw new IllegalStateException("Incomplete head config for HeroDisplayNpc '" + heroName + "'");
        }

        return ItemBuilder.from(Material.SPONGE)
                .modelData(head.hero(), head.texture(), head.skin())
                .build();
    }

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
        if (heroName.equalsIgnoreCase("bug")) {
            GUIManager.open(player, new BugCharmsGUI(player));
            player.sendMessage(Component.text("Configure Bug's charms!", NamedTextColor.LIGHT_PURPLE));
        }
        HeroManager.selectHero(player, heroName);
    }

    @Override
    public void handleSecondaryInteraction(Player player) {
        GUIManager.open(player, new HeroInfoGUI(player, heroName, null));
    }
}
