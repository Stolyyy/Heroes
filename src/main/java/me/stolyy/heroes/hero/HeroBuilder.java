package me.stolyy.heroes.hero;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.strategy.Strategy;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.hero.mechanics.StatusEffects;
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
import java.util.logging.Level;


/**
 * Factory and startup validator building {@link Hero} and {@link MinionHero} instances from
 * hero YAML files using registered ability and mobility constructors.
 */
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
        return buildHero(config, skinId, null);
    }

    private static Hero buildHero(ConfigurationSection config, String skinId, List<String> abilityErrors) {
        String name = Configs.requireString(config, "name");

        // --- Skin Loading ---
        HeroSkin skin = null;
        if (config.contains("skins." + skinId)) {
            skin = new HeroSkin(skinId, Configs.requireSection(config, "skins." + skinId));
        } else if (config.contains("skins.default")) {
            skin = new HeroSkin("default", Configs.requireSection(config, "skins.default"));
        }

        HeroStats stats = parseStats(config);
        DoubleJump mobility = parseMobility(config, stats.mobility(), skin);

        Ability primary = loadAbility(name, config, "abilities.primary", skin, abilityErrors);
        Ability secondary = loadAbility(name, config, "abilities.secondary", skin, abilityErrors);
        Ability smash = loadAbility(name, config, "abilities.smash", skin, abilityErrors);

        List<Ability> passives = parsePassives(name, config, skin, abilityErrors);

        Hero hero = new Hero(name, stats, mobility, primary, secondary, smash, passives);

        ConfigurationSection hurtSec = config.getConfigurationSection("hurt_sound");
        if (hurtSec != null) {
            hero.configureHurtSound(new SoundEffect(hurtSec),
                    (long) (Configs.requireDouble(hurtSec, "cooldown") * 1000),
                    Configs.requireDouble(hurtSec, "min_damage"),
                    Configs.requireDouble(hurtSec, "chance"));
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

        // Kill sound: pulled from `visuals.kill_sound` in the hero's yml (inherited from default.yml).
        SoundEffect killSound = new SoundEffect(statSection(config, "visuals.kill_sound"));
        hero.getEventBus().onKill(victim -> {
            if (hero.getController() != null) {
                killSound.play(hero.getController().getLocation(), hero.getController());
            }
        });

        // Death sound: pulled from `visuals.death_sound`. Fires on every death (respawn and
        // elimination both route through triggerDeath).
        SoundEffect deathSound = new SoundEffect(statSection(config, "visuals.death_sound"));
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
        hero.setWinSound(new SoundEffect(statSection(config, "visuals.win_sound")));

        return hero;
    }

    public static MinionHero buildMinion(ConfigurationSection config, Hero owner) {
        String name = Configs.requireString(config, "name");

        HeroStats stats = parseStats(config);
        DoubleJump mobility = parseMobility(config, stats.mobility(), null);

        Ability primary = loadAbility(name, config, "abilities.primary", null, null);
        Ability secondary = loadAbility(name, config, "abilities.secondary", null, null);
        Ability smash = loadAbility(name, config, "abilities.smash", null, null);

        List<Ability> passives = parsePassives(name, config, null, null);

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
                statDouble(config, "combat.jab_cooldown_ms"),
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

    private static YamlConfiguration requireDefaults() {
        YamlConfiguration defaults = HeroConfig.getConfig("default");
        if (defaults == null) {
            throw new IllegalStateException("heroes/default.yml is not loaded");
        }
        return defaults;
    }

    private static double statDouble(ConfigurationSection config, String path) {
        if (config.contains(path)) return Configs.requireDouble(config, path);
        return Configs.requireDouble(requireDefaults(), path);
    }

    private static int statInt(ConfigurationSection config, String path) {
        if (config.contains(path)) return Configs.requireInt(config, path);
        return Configs.requireInt(requireDefaults(), path);
    }

    private static String statString(ConfigurationSection config, String path) {
        if (config.contains(path)) return Configs.requireString(config, path);
        return Configs.requireString(requireDefaults(), path);
    }

    private static boolean statBool(ConfigurationSection config, String path) {
        if (config.contains(path)) return Configs.requireBoolean(config, path);
        return Configs.requireBoolean(requireDefaults(), path);
    }

    private static ConfigurationSection statSection(ConfigurationSection config, String path) {
        ConfigurationSection local = config.getConfigurationSection(path);
        if (local != null) return local;
        return Configs.requireSection(requireDefaults(), path);
    }

    private static DoubleJump parseMobility(ConfigurationSection config, HeroStats.JumpStats jumpStats, HeroSkin skin) {
        String mobilityKey = statString(config, "mobility.type");
        if (mobilityKey.equalsIgnoreCase("STANDARD")) {
            return new DoubleJump(jumpStats);
        }
        MobilityFactory factory = mobilityRegistry.get(mobilityKey.toLowerCase());
        if (factory == null) {
            throw new IllegalStateException("Unknown mobility.type '" + mobilityKey
                    + "'. Register it in AbilityRegistry.registerAll() or use STANDARD.");
        }
        return factory.create(jumpStats, Configs.requireSection(config, "mobility"), skin);
    }

    private static List<Ability> parsePassives(String heroName, ConfigurationSection config,
                                               HeroSkin skin, List<String> abilityErrors) {
        List<Ability> passives = new ArrayList<>();
        if (!config.contains("abilities.passives")) return passives;

        List<?> rawList = config.getList("abilities.passives");
        if (rawList == null) {
            throw new IllegalStateException("abilities.passives on '" + heroName + "' must be a list");
        }
        for (int i = 0; i < rawList.size(); i++) {
            Object obj = rawList.get(i);
            String label = "abilities.passives[" + i + "]";
            try {
                passives.add(parseAbilityObject(obj, skin));
            } catch (Exception e) {
                recordAbilityFailure(heroName, label, e, abilityErrors);
            }
        }
        return passives;
    }

    private static Ability loadAbility(String heroName, ConfigurationSection config, String path,
                                       HeroSkin skin, List<String> abilityErrors) {
        if (!config.contains(path)) return null;
        try {
            AbilityType slotType = inferSlotType(path);
            ConfigurationSection section = Configs.requireSection(config, path);
            String registryKey = Configs.requireString(section, "type");
            ConfigurationSection copy = copySection(section);
            if (slotType != null) copy.set("type", slotType.name());
            return createAbility(registryKey, copy, skin);
        } catch (Exception e) {
            recordAbilityFailure(heroName, path, e, abilityErrors);
            return null;
        }
    }

    private static void recordAbilityFailure(String heroName, String slotPath, Exception e,
                                             List<String> abilityErrors) {
        String msg = "Failed to build ability '" + slotPath + "' for hero '" + heroName
                + "' — omitting ability: " + e.getMessage();
        Heroes.getInstance().getLogger().log(Level.SEVERE, msg, e);
        if (abilityErrors != null) abilityErrors.add(msg);
    }

    private static AbilityType inferSlotType(String path) {
        if (path.endsWith(".primary")) return AbilityType.PRIMARY;
        if (path.endsWith(".secondary")) return AbilityType.SECONDARY;
        if (path.endsWith(".smash")) return AbilityType.SMASH;
        return null;
    }

    private static Ability parseAbilityObject(Object obj, HeroSkin skin) {
        if (!(obj instanceof Map<?, ?> map)) {
            throw new IllegalStateException("Passive ability entry must be a YAML map, got: " + obj);
        }
        MemoryConfiguration section = new MemoryConfiguration();
        populateSection(section, map);
        String registryKey = Configs.requireString(section, "type");
        AbilityType passiveType;
        if (!section.contains("activation")) {
            passiveType = AbilityType.PASSIVE;
        } else {
            String activation = Configs.requireString(section, "activation");
            passiveType = switch (activation.toUpperCase()) {
                case "TICK" -> AbilityType.PASSIVE;
                case "SNEAK" -> AbilityType.PASSIVE_SNEAK;
                case "DROP" -> AbilityType.PASSIVE_DROP;
                case "SWAP" -> AbilityType.PASSIVE_SWAP;
                default -> throw new IllegalStateException("Unknown passive activation '" + activation
                        + "' (expected TICK, SNEAK, DROP, or SWAP)");
            };
        }
        section.set("type", passiveType.name());
        return createAbility(registryKey, section, skin);
    }

    private static void populateSection(ConfigurationSection section, Map<?, ?> map) {
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
        BiFunction<ConfigurationSection, HeroSkin, Ability> factory = abilityRegistry.get(key.toLowerCase());
        if (factory == null) {
            throw new IllegalStateException("No ability registered for type '" + key
                    + "'. Add it to AbilityRegistry.registerAll().");
        }
        return factory.apply(config, skin);
    }

    /**
     * Builds every hero archetype (and every skin of it) once at startup so a missing or malformed
     * yml key is a boot-time SEVERE naming the hero, not a surprise when a player picks it.
     */
    public static void validateAll() {
        int failures = 0;
        try {
            StatusEffects.validatePresets();
        } catch (Exception e) {
            failures++;
            Heroes.getInstance().getLogger().log(Level.SEVERE,
                    "Status presets in templates.yml invalid: " + e.getMessage(), e);
        }
        for (String heroName : HeroConfig.getAvailableNames()) {
            ConfigurationSection config = HeroConfig.getConfig(heroName);
            try {
                validateBotConfig(heroName, config);
            } catch (Exception e) {
                failures++;
                Heroes.getInstance().getLogger().log(Level.SEVERE,
                        "Hero '" + heroName + "' bot config invalid: " + e.getMessage(), e);
            }

            List<String> skinIds = new ArrayList<>();
            ConfigurationSection skins = config.getConfigurationSection("skins");
            if (skins != null) {
                skinIds.addAll(skins.getKeys(false));
            }
            if (!skinIds.contains("default")) {
                skinIds.add(0, "default");
            }

            for (String skinId : skinIds) {
                List<String> abilityErrors = new ArrayList<>();
                try {
                    buildHero(config, skinId, abilityErrors);
                    failures += abilityErrors.size();
                } catch (Exception e) {
                    failures++;
                    Heroes.getInstance().getLogger().log(Level.SEVERE,
                            "Hero '" + heroName + "' (skin " + skinId + ") does not build: " + e.getMessage(), e);
                }
            }
        }
        Heroes.getInstance().getLogger().info(
                "Validated " + HeroConfig.getAvailableNames().size() + " heroes, " + failures + " failed");
    }

    private static void validateBotConfig(String heroName, ConfigurationSection config) {
        String strategyType = Configs.requireString(config, "bot.strategy_type");
        if (!Strategy.isKnownType(strategyType)) {
            throw new IllegalStateException("Hero '" + heroName + "' has unknown bot.strategy_type '" + strategyType + "'");
        }
        Configs.requireSection(config, "ai");
        Configs.requireSection(config, "strategy");
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
}
