package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.kit.bug.charms.Charm;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class BugCharms extends BaseAbility {
    private final EnumMap<Charms, Charm> registry = new EnumMap<>(Charms.class);
    private final Set<Charms> equipped = new HashSet<>();

    private boolean ultimateActive = false;
    private boolean initialized = false;

    private final float baseSoulPerHit;

    public BugCharms(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.baseSoulPerHit = (float) Configs.requireDouble(this.config, "base_soul_per_hit");
        for (Charms charm : Charms.values()) {
            registry.put(charm, CharmConfig.createCharm(charm));
        }
    }

    public static Set<Charms> rollRandomPreset() {
        YamlConfiguration cfg = HeroConfig.getConfig("bug");
        if (cfg == null) {
            throw new IllegalStateException("Missing loaded hero config 'bug' for charm presets");
        }
        List<?> presets = cfg.getList("charm_presets");
        if (presets == null || presets.isEmpty()) return Set.of();

        Object choice = presets.get(ThreadLocalRandom.current().nextInt(presets.size()));
        Set<Charms> result = new HashSet<>();
        for (Object name : (List<?>) choice) {
            result.add(Charms.valueOf(name.toString().toUpperCase(Locale.ROOT)));
        }
        return result;
    }

    private void lateInitialize(Hero hero) {
        equipped.addAll(HeroManager.getEquippedCharms(hero.getController().getUniqueId()));
        for (Charms charm : equipped) {
            registry.get(charm).activate(hero);
        }
    }

    public void setUltimateActive(Hero hero, boolean active) {
        if (this.ultimateActive == active) return;
        this.ultimateActive = active;

        for (Charms charm : Charms.values()) {
            if (equipped.contains(charm)) continue;
            Charm impl = registry.get(charm);
            if (active) impl.activate(hero);
            else impl.deactivate(hero);
        }
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        double multiplier = 1.0;
        for (Charm charm : registry.values()) {
            if (charm.isActive()) {
                multiplier *= charm.soulMultiplier();
            }
        }
        hero.getEnergy().changeEnergy((float) (baseSoulPerHit * multiplier));

        for (Charm charm : registry.values()) {
            if (charm.isActive()) charm.onJabHit(hero, victim);
        }
    }

    @Override
    public void onAbilityHit(Hero hero, Ability ability, LivingEntity victim) {
        for (Charm charm : registry.values()) {
            if (charm.isActive()) charm.onAbilityHit(hero, ability, victim);
        }
    }

    @Override
    public void onDamageTaken(Hero hero, Entity source, double amount) {
        for (Charm charm : registry.values()) {
            if (charm.isActive()) charm.onDamageTaken(hero, source, amount);
        }
    }

    @Override
    protected void onTick(Hero hero) {
        if (!initialized && hero.getController() != null) {
            lateInitialize(hero);
            initialized = true;
        }
        for (Charm charm : registry.values()) {
            if (charm.isActive()) charm.tick(hero);
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        for (Charm charm : registry.values()) {
            if (charm.isActive()) charm.onDeath(hero);
        }
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        setUltimateActive(hero, false);
        for (Charm charm : registry.values()) {
            if (charm.isActive()) {
                charm.onOwnerLost(hero);
                charm.deactivate(hero);
            }
        }
    }

    @Override
    public List<Component> passiveCardExtras(Controller controller) {
        Set<Charms> equippedCharms = HeroManager.getEquippedCharms(controller.getUniqueId());
        if (equippedCharms.isEmpty()) return List.of();

        List<Component> lines = new ArrayList<>();
        lines.add(GuiText.component("gui.charms.equipped-header"));
        for (Charms charm : Charms.values()) {
            if (equippedCharms.contains(charm)) {
                lines.add(Component.text("\u2022 " + HeroConfig.formatName(charm.name()), NamedTextColor.WHITE));
            }
        }
        return lines;
    }

    @Override
    public void onCosmeticsApplied(Controller controller) {
        Set<Charms> equippedCharms = HeroManager.getEquippedCharms(controller.getUniqueId());
        int slot = CosmeticManager.INV_TOP_START;
        for (Charms charm : Charms.values()) {
            if (!equippedCharms.contains(charm) || slot > CosmeticManager.INV_TOP_END) continue;
            controller.setInventoryItem(slot++, ItemBuilder.from(Material.PAPER)
                    .name(GuiText.component("gui.charms.charm-name", "charm", HeroConfig.formatName(charm.name())))
                    .lore(Component.text(CharmConfig.getDescription(charm), NamedTextColor.GRAY))
                    .modelData("gui", "charms", CharmConfig.getTexture(charm))
                    .build());
        }
    }

    @Override
    public void perform(Hero hero) {}
}
