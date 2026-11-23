package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.kit.bug.charms.*;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

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

    private float baseSoulPerHit;

    public BugCharms(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
    }

    public static Set<Charms> rollRandomPreset() {
        org.bukkit.configuration.file.YamlConfiguration cfg =
                me.stolyy.heroes.hero.HeroConfig.getConfig("bug");
        if (cfg == null) return Set.of();
        List<?> presets = cfg.getList("charm_presets");
        if (presets == null || presets.isEmpty()) return Set.of();

        Object choice = presets.get(ThreadLocalRandom.current().nextInt(presets.size()));
        Set<Charms> result = new HashSet<>();
        for (Object name : (List<?>) choice) {
            result.add(Charms.valueOf(name.toString().toUpperCase(Locale.ROOT)));
        }
        return result;
    }

    @Override
    public void initialize(Hero hero) {
        this.baseSoulPerHit = (float) config.getDouble("base_soul_per_hit", 17.0);

        registry.put(Charms.SPRINTMASTER,   new Sprintmaster(config.getConfigurationSection("sprintmaster")));
        registry.put(Charms.STEADY_BODY,    new SteadyBody(config.getConfigurationSection("steady_body")));
        registry.put(Charms.QUICK_SLASH,    new QuickSlash(config.getConfigurationSection("quick_slash")));
        registry.put(Charms.STRENGTH,       new Strength(config.getConfigurationSection("strength")));
        registry.put(Charms.MARK_OF_PRIDE,  new MarkOfPride(config.getConfigurationSection("mark_of_pride")));
        registry.put(Charms.DASHMASTER,     new Dashmaster(config.getConfigurationSection("dashmaster")));
        registry.put(Charms.SHARP_SHADOW,   new SharpShadow(config.getConfigurationSection("sharp_shadow")));
        registry.put(Charms.SOUL_TWISTER,   new SoulTwister(config.getConfigurationSection("soul_twister")));
        registry.put(Charms.SHAMAN_STONE,   new ShamanStone(config.getConfigurationSection("shaman_stone")));
        registry.put(Charms.KINGSOUL,       new Kingsoul(config.getConfigurationSection("kingsoul")));
        registry.put(Charms.WEAVERSONG,     new Weaversong(config.getConfigurationSection("weaversong")));
        registry.put(Charms.SOUL_EATER,     new SoulEater(config.getConfigurationSection("soul_eater")));
        registry.put(Charms.GRUBSONG,       new Grubsong(config.getConfigurationSection("grubsong")));
        registry.put(Charms.NAIL_MASTER,    new NailMaster(config.getConfigurationSection("nail_master")));
        registry.put(Charms.HEAVY_BLOW,     new HeavyBlow(config.getConfigurationSection("heavy_blow")));
    }

    private void lateInitialize(Hero hero) {
        equipped.addAll(HeroManager.getEquippedCharms(hero.getController().getUniqueId()));
        for (Charms charm : equipped) {
            Charm impl = registry.get(charm);
            if (impl != null) impl.activate(hero);
        }
    }

    public void setUltimateActive(Hero hero, boolean active) {
        if (this.ultimateActive == active) return;
        this.ultimateActive = active;

        for (Charms charm : Charms.values()) {
            if (equipped.contains(charm)) continue;
            Charm impl = registry.get(charm);
            if (impl == null) continue;

            if (active) impl.activate(hero);
            else impl.deactivate(hero);
        }
    }

    @Override
    public void onJabHit(Hero hero, LivingEntity victim) {
        double multiplier = 1.0;
        Charm soulEater = registry.get(Charms.SOUL_EATER);
        if (soulEater instanceof SoulEater se && se.isActive()) {
            multiplier = se.getMultiplier();
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
    public void perform(Hero hero) {}
}
