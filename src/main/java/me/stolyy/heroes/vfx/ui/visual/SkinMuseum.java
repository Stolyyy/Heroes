package me.stolyy.heroes.vfx.ui.visual;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.DisplayTags;
import me.stolyy.heroes.vfx.effect.TextDisplayBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Physical lobby Skin Museum / cosmetic hall spawned from {@code lobby.yml museum:}.
 *
 * <p>Each non-admin-only hero gets one row (alphabetical by display name), with one labelled
 * non-persistent {@link ArmorStand} per non-hidden skin ({@code default} first, collectible skins
 * ordered normal/rare/legendary then by display name, {@code masterskin} last).
 *
 * <p>Note: reloading hero configs via {@code /balance reload} does not rebuild the museum; a
 * server restart picks up newly added skins.
 */
public final class SkinMuseum {
    private static final double LABEL_Y_OFFSET = 2.35;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final Comparator<HeroSkin> SKIN_ORDER = Comparator
            .comparingInt(SkinMuseum::skinOrderBucket)
            .thenComparing(HeroSkin::getDisplayName, String.CASE_INSENSITIVE_ORDER);

    private static boolean listenerRegistered;
    private final List<Entity> spawned = new ArrayList<>();

    /** Registers the protection listener preventing players from stripping or altering museum stands. */
    public static void initialize() {
        if (listenerRegistered) return;
        Bukkit.getPluginManager().registerEvents(new ProtectionListener(), Heroes.getInstance());
        listenerRegistered = true;
    }

    public void spawn() {
        despawn();
        DisplayTags.sweep(DisplayTags.SKIN_MUSEUM);

        File file = new File(Heroes.getInstance().getDataFolder(), "lobby.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("museum");
        if (section == null || !section.getBoolean("enabled", false)) return;

        String worldName = Configs.requireString(section, "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("lobby.yml museum.world '" + worldName + "' is not loaded");
        }

        ConfigurationSection origin = Configs.requireSection(section, "origin");
        double originX = Configs.requireDouble(origin, "x");
        double originY = Configs.requireDouble(origin, "y");
        double originZ = Configs.requireDouble(origin, "z");
        float yaw = (float) Configs.requireDouble(section, "yaw");

        ConfigurationSection skinStep = Configs.requireSection(section, "skin-step");
        double skinStepX = Configs.requireDouble(skinStep, "x");
        double skinStepZ = Configs.requireDouble(skinStep, "z");

        ConfigurationSection heroStep = Configs.requireSection(section, "hero-step");
        double heroStepX = Configs.requireDouble(heroStep, "x");
        double heroStepZ = Configs.requireDouble(heroStep, "z");

        List<String> heroIds = HeroConfig.getAvailableNames().stream()
                .filter(id -> !HeroConfig.isAdminOnly(id))
                .sorted(Comparator.comparing(HeroConfig::formatName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (int row = 0; row < heroIds.size(); row++) {
            String heroId = heroIds.get(row);
            List<HeroSkin> skins = new ArrayList<>(CosmeticManager.selectableSkins(heroId));
            skins.sort(SKIN_ORDER);

            for (int col = 0; col < skins.size(); col++) {
                HeroSkin skin = skins.get(col);
                double x = originX + row * heroStepX + col * skinStepX;
                double z = originZ + row * heroStepZ + col * skinStepZ;
                Location standLoc = new Location(world, x, originY, z, yaw, 0f);
                spawnPedestal(heroId, skin, standLoc);
            }
        }
    }

    private void spawnPedestal(String heroId, HeroSkin skin, Location standLoc) {
        standLoc.getChunk().setForceLoaded(true);
        standLoc.getChunk().load();

        Map<EquipmentSlot, ItemStack> armor = CosmeticManager.buildArmorEquipment(heroId, skin);
        ItemStack primaryItem = CosmeticManager.buildPrimaryItem(heroId, skin);

        ArmorStand stand = (ArmorStand) standLoc.getWorld().spawnEntity(
                standLoc, EntityType.ARMOR_STAND, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
                    ArmorStand as = (ArmorStand) entity;
                    as.setGravity(false);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.setArms(true);
                    as.setCanPickupItems(false);
                    DisplayTags.tag(as, DisplayTags.SKIN_MUSEUM);

                    EntityEquipment eq = as.getEquipment();
                    for (Map.Entry<EquipmentSlot, ItemStack> entry : armor.entrySet()) {
                        eq.setItem(entry.getKey(), entry.getValue());
                    }
                    eq.setItemInMainHand(primaryItem);
                });
        spawned.add(stand);

        Location labelLoc = standLoc.clone().add(0, LABEL_Y_OFFSET, 0);
        spawned.add(TextDisplayBuilder.create(labelLoc)
                .text(buildLabel(heroId, skin))
                .billboard(Display.Billboard.CENTER)
                .transparentBackground()
                .tag(DisplayTags.SKIN_MUSEUM)
                .spawn());
    }

    private static Component buildLabel(String heroId, HeroSkin skin) {
        Component line1 = GuiText.component("visual.museum.line-1", "skin", skin.getDisplayName());

        String rarityRaw = skin.getRarity() != null
                ? GuiText.raw("gui.skins.rarity." + skin.getRarity().name().toLowerCase(Locale.ROOT))
                : "";
        String line2Template = GuiText.raw("visual.museum.line-2")
                .replace("{hero}", MM.escapeTags(HeroConfig.formatName(heroId)))
                .replace("{rarity}", rarityRaw)
                .trim();
        Component line2 = MM.deserialize(line2Template);

        return Component.empty().append(line1).append(Component.newline()).append(line2);
    }

    private static int skinOrderBucket(HeroSkin skin) {
        if (skin.getId().equalsIgnoreCase("default")) return 0;
        if (skin.getId().equalsIgnoreCase("masterskin")) return 4;
        HeroSkin.Rarity rarity = skin.getRarity();
        if (rarity == null) return 1;
        return switch (rarity) {
            case NORMAL -> 1;
            case RARE -> 2;
            case LEGENDARY -> 3;
        };
    }

    public void despawn() {
        spawned.forEach(Entity::remove);
        spawned.clear();
    }

    private static final class ProtectionListener implements Listener {
        @EventHandler
        public void onManipulate(PlayerArmorStandManipulateEvent event) {
            if (event.getRightClicked().getPersistentDataContainer()
                    .has(DisplayTags.SKIN_MUSEUM, PersistentDataType.BYTE)) {
                event.setCancelled(true);
            }
        }
    }
}
