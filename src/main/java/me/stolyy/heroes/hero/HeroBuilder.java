package me.stolyy.heroes.hero;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;


public class HeroBuilder {
    private static final Map<String, BiFunction<ConfigurationSection, HeroSkin, Ability>> abilityRegistry = new HashMap<>();
    private static final Map<String, MobilityFactory> mobilityRegistry = new HashMap<>();

    @FunctionalInterface
    public interface MobilityFactory {
        DoubleJump create(HeroStats.JumpStats stats, ConfigurationSection config, HeroSkin skin);
    }

    public static void registerAbility(String key, BiFunction<ConfigurationSection, HeroSkin, Ability> abilityFactory) {
        abilityRegistry.put(key.toLowerCase(), abilityFactory);
    }

    public static void registerMobility(String key, MobilityFactory mobilityFactory) {
        mobilityRegistry.put(key.toLowerCase(), mobilityFactory);
    }

    public static Hero buildHero(ConfigurationSection config) {
        return buildHero(config, "default");
    }

    public static Hero buildHero(ConfigurationSection config, String skinId) {
        String name = config.getString("name", "Unknown Hero");

        // --- Skin Loading ---
        HeroSkin skin = null;
        if (config.contains("skins." + skinId)) {
            skin = new HeroSkin(skinId, config.getConfigurationSection("skins." + skinId));
        } else if (config.contains("skins.default")) {
            skin = new HeroSkin("default", config.getConfigurationSection("skins.default"));
        }

        HeroStats stats = parseStats(config);
        DoubleJump mobility = parseMobility(config, stats.mobility(), skin);

        Ability primary = loadAbility(config, "abilities.primary", skin);
        Ability secondary = loadAbility(config, "abilities.secondary", skin);
        Ability smash = loadAbility(config, "abilities.smash", skin);
        if (smash == null) smash = loadAbility(config, "abilities.ultimate", skin);

        List<Ability> passives = parsePassives(config, skin);

        Hero hero = new Hero(name, stats, mobility, primary, secondary, smash, passives);

        ConfigurationSection hurtSec = config.getConfigurationSection("hurt_sound");
        if (hurtSec != null) {
            hero.configureHurtSound(new SoundEffect(hurtSec),
                    (long) (hurtSec.getDouble("cooldown", 0.5) * 1000),
                    hurtSec.getDouble("min_damage", 0),
                    hurtSec.getDouble("chance", 1.0));
        }
        hero.setSkin(skin);

        // Pain sound: play {heroname}.pain when the hero takes damage
        String painSoundKey = name.toLowerCase().replace(" ", "_") + ".pain";
        hero.getEventBus().onDamageTaken((source, amount) -> {
            Controller controller = hero.getController();
            // A controller whose entity is gone (a bot mid-death) hands back a null location.
            // SoundBuilder.play dereferences it, and this listener runs inside applyDamage —
            // the NPE aborted the damage handler before any health was ever written, which is
            // what made bots unharmable after their first death.
            if (controller == null || controller.getLocation() == null) return;
            SoundBuilder.of(painSoundKey).volume(1.0f).pitch(1.0f).play(controller.getLocation());
        });

        // Kill sound: pulled from `visuals.kill_sound` in the hero's yml. No-op if unconfigured.
        SoundEffect killSound = new SoundEffect(config.getConfigurationSection("visuals.kill_sound"));
        hero.getEventBus().onKill(victim -> {
            if (hero.getController() != null) {
                killSound.play(hero.getController().getLocation(), hero.getController());
            }
        });

        // Death sound: pulled from `visuals.death_sound`. Fires on every death (respawn and
        // elimination both route through triggerDeath). No-op if unconfigured.
        SoundEffect deathSound = new SoundEffect(config.getConfigurationSection("visuals.death_sound"));
        hero.getEventBus().onDeath(deathLocation -> {
            Controller controller = hero.getController();
            if (controller == null) return;
            // Prefer the location captured at death: by the time this fires the entity has
            // already been moved to its respawn or spectator point.
            Location location = deathLocation != null ? deathLocation : controller.getLocation();
            deathSound.play(location, controller);
        });

        // Win sound: pulled from `visuals.win_sound`. Played at game end by Game via the
        // chosen winning player's hero (see Game.playWinSound).
        hero.setWinSound(new SoundEffect(config.getConfigurationSection("visuals.win_sound")));

        return hero;
    }

    public static MinionHero buildMinion(ConfigurationSection config, Hero owner) {
        String name = config.getString("name", "Minion");

        HeroStats stats = parseStats(config);
        DoubleJump mobility = parseMobility(config, stats.mobility(), null);

        Ability primary = loadAbility(config, "abilities.primary", null);
        Ability secondary = loadAbility(config, "abilities.secondary", null);
        Ability smash = loadAbility(config, "abilities.smash", null);
        if (smash == null) smash = loadAbility(config, "abilities.ultimate", null);

        List<Ability> passives = parsePassives(config, null);

        MinionHero minion = new MinionHero(name, stats, mobility, primary, secondary, smash, passives, owner);

        // MANDATORY on every minion section: whether the minion can be hit at all (enforced
        // victim-only in Game.canTarget). Required rather than defaulted, so adding a minion
        // without deciding this is a load-time error instead of a silently killable — or silently
        // immortal — summon.
        minion.setInvincible(Configs.requireBoolean(config, "invincible"));
        minion.setGrantsCharge(Configs.orTemplate(config, "grants_charge", "minion.grants_charge"));

        ConfigurationSection skinSection = config.getConfigurationSection("skin");
        if (skinSection != null) {
            minion.setSkin(new HeroSkin("default", skinSection));
        }

        return minion;
    }

    private static HeroStats parseStats(ConfigurationSection config) {
        // Stat reads consult default.yml as a fallback for any field the section doesn't declare.
        // For top-level hero configs this is redundant (Bukkit's setDefaults already covers it
        // via copyDefaults). For minion subsections (e.g. bug.yml: weaversong.minion) the parent
        // yml's defaults are matched by absolute path and don't apply to the nested subsection,
        // so the helpers below are what actually rescue gravity/jump_height/step_height etc.
        String typeStr = statString(config, "stats.type");
        if (typeStr == null) typeStr = "HYBRID";
        HeroType type = HeroType.valueOf(typeStr.toUpperCase());
        HeroStats.AttributeStats attributes = new HeroStats.AttributeStats(type,
                statDouble(config, "stats.weight"),
                statDouble(config, "stats.health"),
                statDouble(config, "stats.speed"),
                statDouble(config, "stats.jump_height"),
                statDouble(config, "stats.gravity"),
                statDouble(config, "stats.step_height"),
                statDouble(config, "stats.scale"));

        HeroStats.JumpStats jumpStats = new HeroStats.JumpStats(
                statInt(config, "mobility.max_jumps"),
                statDouble(config, "mobility.x_strength"),
                statDouble(config, "mobility.y_strength"),
                new SoundEffect(statSection(config, "mobility.sound")),
                new VisualEffect(statSection(config, "mobility.effect"))
        );

        HeroStats.CombatStats combatStats = new HeroStats.CombatStats(
                statDouble(config, "combat.jab_range"),
                statDouble(config, "combat.jab_radius"),
                statBool(config, "combat.jab_pierce"),
                statDouble(config, "combat.jab_cooldown"),
                statDouble(config, "combat.jab_damage"),
                statDouble(config, "combat.jab_knockback"),
                new SoundEffect(statSection(config, "combat.swing_sound")),
                new SoundEffect(statSection(config, "combat.hit_sound"))
        );

        HeroStats.EnergyStats energyStats = new HeroStats.EnergyStats(
                statInt(config, "energy.starting_energy"),
                statInt(config, "energy.max_energy"),
                statDouble(config, "energy.energy_per_tick"),
                statBool(config, "energy.can_increase_energy"));

        return new HeroStats(attributes, jumpStats, combatStats, energyStats);
    }

    // ---------- defaults-aware stat helpers ----------
    // For top-level hero configs `config.contains(path)` is true via Bukkit's copyDefaults, so
    // these fall through to the section. For nested minion subsections the section doesn't
    // inherit defaults at this nested path, so we consult default.yml directly.

    private static double statDouble(ConfigurationSection config, String path) {
        if (config.contains(path)) return config.getDouble(path);
        YamlConfiguration defaults = HeroConfig.getConfig("default");
        return defaults != null ? defaults.getDouble(path) : 0;
    }

    private static int statInt(ConfigurationSection config, String path) {
        if (config.contains(path)) return config.getInt(path);
        YamlConfiguration defaults = HeroConfig.getConfig("default");
        return defaults != null ? defaults.getInt(path) : 0;
    }

    private static String statString(ConfigurationSection config, String path) {
        if (config.contains(path)) return config.getString(path);
        YamlConfiguration defaults = HeroConfig.getConfig("default");
        return defaults != null ? defaults.getString(path) : null;
    }

    private static boolean statBool(ConfigurationSection config, String path) {
        if (config.contains(path)) return config.getBoolean(path);
        YamlConfiguration defaults = HeroConfig.getConfig("default");
        return defaults != null && defaults.getBoolean(path);
    }

    private static ConfigurationSection statSection(ConfigurationSection config, String path) {
        ConfigurationSection local = config.getConfigurationSection(path);
        if (local != null) return local;
        YamlConfiguration defaults = HeroConfig.getConfig("default");
        return defaults != null ? defaults.getConfigurationSection(path) : null;
    }

    private static DoubleJump parseMobility(ConfigurationSection config, HeroStats.JumpStats jumpStats, HeroSkin skin) {
        String mobilityKey = config.getString("mobility.type", "STANDARD");
        if (mobilityRegistry.containsKey(mobilityKey.toLowerCase())) {
            ConfigurationSection mobilityConfig = config.getConfigurationSection("mobility");
            return mobilityRegistry.get(mobilityKey.toLowerCase()).create(jumpStats, mobilityConfig, skin);
        }
        return new DoubleJump(jumpStats);
    }

    private static List<Ability> parsePassives(ConfigurationSection config, HeroSkin skin) {
        List<Ability> passives = new ArrayList<>();
        if (config.contains("abilities.passives")) {
            for (Object obj : config.getList("abilities.passives")) passives.add(parseAbilityObject(obj, skin));
        }
        return passives;
    }

    private static Ability loadAbility(ConfigurationSection config, String path, HeroSkin skin) {
        AbilityType slotType = inferSlotType(path);
        if (config.isConfigurationSection(path)) {
            ConfigurationSection section = config.getConfigurationSection(path);
            String registryKey = section.getString("type");
            ConfigurationSection copy = copySection(section);
            if (slotType != null) copy.set("type", slotType.name());
            return createAbility(registryKey, copy, skin);
        } else {
            return createAbility(config.getString(path), null, skin);
        }
    }

    private static AbilityType inferSlotType(String path) {
        if (path.endsWith(".primary")) return AbilityType.PRIMARY;
        if (path.endsWith(".secondary")) return AbilityType.SECONDARY;
        if (path.endsWith(".smash") || path.endsWith(".ultimate")) return AbilityType.SMASH;
        return null;
    }

    private static Ability parseAbilityObject(Object obj, HeroSkin skin) {
        if (obj instanceof String) return createAbility((String) obj, null, skin);
        if (obj instanceof Map) {
            MemoryConfiguration section = new MemoryConfiguration();
            populateSection(section, (Map<?, ?>) obj);
            String registryKey = section.getString("type");
            String activation = section.getString("activation", "TICK");
            AbilityType passiveType = switch (activation.toUpperCase()) {
                case "SNEAK" -> AbilityType.PASSIVE_SNEAK;
                case "DROP" -> AbilityType.PASSIVE_DROP;
                case "SWAP" -> AbilityType.PASSIVE_SWAP;
                default -> AbilityType.PASSIVE;
            };
            section.set("type", passiveType.name());
            return createAbility(registryKey, section, skin);
        }
        return createDummy("Invalid");
    }

    private static void populateSection(org.bukkit.configuration.ConfigurationSection section, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nestedMap) {
                populateSection(section.createSection(key), nestedMap);
            } else {
                section.set(key, value);
            }
        }
    }

    private static Ability createAbility(String key, ConfigurationSection config, HeroSkin skin) {
        // An absent slot (no key at all) is a legitimate empty slot, e.g. a hero whose secondary is
        // still unwritten. A key that names nothing in the registry is a wiring mistake and fails loudly.
        if (key == null) return createDummy("Null");
        if (!abilityRegistry.containsKey(key.toLowerCase())) {
            throw new IllegalStateException("No ability registered for type '" + key
                    + "'. Add it to AbilityRegistry.registerAll().");
        }
        return abilityRegistry.get(key.toLowerCase()).apply(config, skin);
    }

    /**
     * Builds every hero archetype (and every skin of it) once at startup so a missing or malformed
     * yml key is a boot-time SEVERE naming the hero, not an exception when a player picks it.
     */
    public static void validateAll() {
        int failures = 0;
        for (String heroName : HeroConfig.getAvailableNames()) {
            ConfigurationSection config = HeroConfig.getConfig(heroName);
            List<String> skinIds = new ArrayList<>(List.of("default"));
            ConfigurationSection skins = config.getConfigurationSection("skins");
            if (skins != null) skinIds.addAll(skins.getKeys(false));
            for (String skinId : skinIds) {
                try {
                    buildHero(config, skinId);
                } catch (Exception e) {
                    failures++;
                    me.stolyy.heroes.Heroes.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                            "Hero '" + heroName + "' (skin " + skinId + ") does not build: " + e.getMessage(), e);
                }
            }
        }
        me.stolyy.heroes.Heroes.getInstance().getLogger().info(
                "Validated " + HeroConfig.getAvailableNames().size() + " heroes, " + failures + " failed");
    }

    private static ConfigurationSection copySection(ConfigurationSection source) {
        MemoryConfiguration copy = new MemoryConfiguration();
        for (String key : source.getKeys(true)) {
            if (!source.isConfigurationSection(key)) {
                copy.set(key, source.get(key));
            }
        }
        return copy;
    }

    private static Ability createDummy(String name) {
        return new Ability() {
            @Override public String getName() { return "Missing: " + name; }
            @Override public String getDescription() { return ""; }
            @Override public AbilityType getType() { return AbilityType.PASSIVE; }
            @Override public long getCooldown() { return 0; }
            @Override public int getEnergyCost() { return 0; }
            @Override public Ammo getAmmo() { return null; }
            @Override public Cooldown getCooldownObject() { return new Cooldown(0, 0, 0); }
            @Override public boolean execute(Hero h) { return false; }
            @Override public boolean canExecute(Hero hero) { return false; }
        };
    }
}
