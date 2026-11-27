package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.TemplateConfig;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-hero status effect registry driven by generic toggle/feedback specs with optional presets
 * layered over {@code templates.yml status.defaults}.
 *
 * <p>Abilities apply effects through {@link #applyTo} so hero and non-hero targets (practice dummies)
 * share one entry point. Restrictions are the union of all active effects. End feedback plays on
 * expiry, knockback break, and explicit source removal ({@link #removeAllFrom}), and is skipped when
 * effects are cleared by death, respawn, elimination, or {@link Hero#cleanup()}.
 */
public final class StatusEffects {
    private static final double HOLD_EPSILON_SQ = 0.01 * 0.01;
    private static final Object MOVE_MODIFIER_KEY = new Object();
    private static final Object HOLD_MODIFIER_KEY = new Object();

    // Active hero instances currently carrying at least one effect, for O(active) removeAllFrom.
    private static final Set<Hero> ACTIVE_HEROES = ConcurrentHashMap.newKeySet();
    // Non-hero victims (practice dummies): no inputs to block, but timers, hold, knockback break,
    // feedback, and onExpire callbacks run identically so finishers and status cues work in practice.
    private static final Map<UUID, List<ActiveEffect>> NON_HERO_EFFECTS = new HashMap<>();

    /**
     * Validates {@code templates.yml} {@code status.defaults} and every entry under
     * {@code status.presets} at startup so malformed toggles, feedback blocks, or missing
     * {@code messages.yml status.indicator.<preset>} keys surface immediately even when unused.
     */
    public static void validatePresets() {
        Spec.validateDefaults();
        for (String presetId : TemplateConfig.getConfigKeys("status.presets")) {
            Spec.resolve(null, presetId, presetId.toLowerCase(Locale.ROOT), -1L);
        }
    }

    public enum Restriction {
        MOVE,
        JUMP,
        ABILITIES,
        JAB
    }

    /** Sound and visual hooks resolved across ability block, preset, and {@code templates.yml status.defaults}. */
    public record Feedback(
            SoundEffect applySound,
            VisualEffect applyVisual,
            SoundEffect endSound,
            VisualEffect endVisual,
            SoundEffect loopSound,
            VisualEffect loopVisual,
            int loopIntervalTicks
    ) {}

    /**
     * Immutable specification for a status effect resolved in three layers:
     * ability status block → optional preset in {@code templates.yml status.presets} → {@code status.defaults}.
     */
    public record Spec(
            String id,
            long durationMs,
            boolean blockMove,
            boolean blockJump,
            boolean blockAbilities,
            boolean blockJab,
            boolean hold,
            boolean breakOnKnockback,
            Feedback feedback
    ) {
        /** Reads a timed {@code status:} section ({@code duration} required, plus {@code preset} and/or {@code id}). */
        public static Spec fromConfig(ConfigurationSection parent) {
            return fromConfig(parent, "status");
        }

        /** Reads a timed status section at {@code key} ({@code duration} required, plus {@code preset} and/or {@code id}). */
        public static Spec fromConfig(ConfigurationSection parent, String key) {
            ConfigurationSection section = Configs.requireSection(parent, key);
            double durationSec = Configs.requireDouble(section, "duration");
            if (!Double.isFinite(durationSec) || durationSec < 0) {
                throw new IllegalStateException("Status block '" + section.getCurrentPath()
                        + "' has invalid duration: " + durationSec);
            }
            return parseSection(section, Math.round(durationSec * 1000.0));
        }

        /** Reads an indefinite {@code status:} section (no fixed duration; rejects a {@code duration} key). */
        public static Spec fromConfigIndefinite(ConfigurationSection parent) {
            return fromConfigIndefinite(parent, "status");
        }

        /** Reads an indefinite status section at {@code key} (no fixed duration; rejects a {@code duration} key). */
        public static Spec fromConfigIndefinite(ConfigurationSection parent, String key) {
            ConfigurationSection section = Configs.requireSection(parent, key);
            if (section.contains("duration")) {
                throw new IllegalStateException("Indefinite status block '" + section.getCurrentPath()
                        + "' must not declare 'duration'");
            }
            return parseSection(section, -1L);
        }

        private static Spec parseSection(ConfigurationSection section, long durationMs) {
            String preset = section.contains("preset")
                    ? requireValidPreset(Configs.requireString(section, "preset"))
                    : null;
            String id;
            if (section.contains("id")) {
                id = Configs.requireString(section, "id").trim().toLowerCase(Locale.ROOT);
                if (id.isEmpty()) {
                    throw new IllegalStateException("Status block '" + section.getCurrentPath()
                            + "' has an empty 'id'");
                }
            } else if (preset != null) {
                id = preset;
            } else {
                throw new IllegalStateException("Status block '" + section.getCurrentPath()
                        + "' must declare 'preset' or 'id'");
            }
            return resolve(section, preset, id, durationMs);
        }

        private static void validateDefaults() {
            ConfigurationSection defaults = TemplateConfig.requireSection("status.defaults");
            resolveTogglesAndFeedback(null, null, defaults, "status.defaults");
        }

        private static Spec resolve(@Nullable ConfigurationSection ability, @Nullable String presetName,
                                    String id, long durationMs) {
            ConfigurationSection defaults = TemplateConfig.requireSection("status.defaults");
            ConfigurationSection preset = presetName != null
                    ? TemplateConfig.requireSection("status.presets." + presetName)
                    : null;
            GameConfigManager.getMessageString("status.indicator." + id);
            ResolvedBody body = resolveTogglesAndFeedback(ability, preset, defaults, id);
            return new Spec(
                    id,
                    durationMs,
                    body.blockMove,
                    body.blockJump,
                    body.blockAbilities,
                    body.blockJab,
                    body.hold,
                    body.breakOnKnockback,
                    body.feedback
            );
        }

        private record ResolvedBody(
                boolean blockMove,
                boolean blockJump,
                boolean blockAbilities,
                boolean blockJab,
                boolean hold,
                boolean breakOnKnockback,
                Feedback feedback
        ) {}

        private static ResolvedBody resolveTogglesAndFeedback(@Nullable ConfigurationSection ability,
                                                              @Nullable ConfigurationSection preset,
                                                              ConfigurationSection defaults,
                                                              String label) {
            boolean blockMove = resolveBoolean(ability, preset, defaults, "block_move");
            boolean blockJump = resolveBoolean(ability, preset, defaults, "block_jump");
            boolean blockAbilities = resolveBoolean(ability, preset, defaults, "block_abilities");
            boolean blockJab = resolveBoolean(ability, preset, defaults, "block_jab");
            boolean hold = resolveBoolean(ability, preset, defaults, "hold");
            boolean breakOnKnockback = resolveBoolean(ability, preset, defaults, "break_on_knockback");

            int loopIntervalTicks = resolveInt(ability, preset, defaults, "loop_interval_ticks");
            if (loopIntervalTicks <= 0) {
                throw new IllegalStateException("Status '" + label
                        + "' loop_interval_ticks must be > 0, got " + loopIntervalTicks);
            }

            Feedback feedback = new Feedback(
                    resolveRequiredSound(ability, preset, defaults, "apply_sound"),
                    resolveOptionalVisual(ability, preset, defaults, "apply_visual"),
                    resolveOptionalSound(ability, preset, defaults, "end_sound"),
                    resolveOptionalVisual(ability, preset, defaults, "end_visual"),
                    resolveOptionalSound(ability, preset, defaults, "loop_sound"),
                    resolveOptionalVisual(ability, preset, defaults, "loop_visual"),
                    loopIntervalTicks
            );
            return new ResolvedBody(blockMove, blockJump, blockAbilities, blockJab, hold, breakOnKnockback, feedback);
        }

        private static boolean resolveBoolean(@Nullable ConfigurationSection ability,
                                              @Nullable ConfigurationSection preset,
                                              ConfigurationSection defaults,
                                              String key) {
            if (ability != null && ability.contains(key)) return Configs.requireBoolean(ability, key);
            if (preset != null && preset.contains(key)) return Configs.requireBoolean(preset, key);
            return Configs.requireBoolean(defaults, key);
        }

        private static int resolveInt(@Nullable ConfigurationSection ability,
                                      @Nullable ConfigurationSection preset,
                                      ConfigurationSection defaults,
                                      String key) {
            if (ability != null && ability.contains(key)) return Configs.requireInt(ability, key);
            if (preset != null && preset.contains(key)) return Configs.requireInt(preset, key);
            return Configs.requireInt(defaults, key);
        }

        private static SoundEffect resolveRequiredSound(@Nullable ConfigurationSection ability,
                                                        @Nullable ConfigurationSection preset,
                                                        ConfigurationSection defaults,
                                                        String key) {
            ConfigurationSection owner = pickLayer(ability, preset, defaults, key);
            if (owner == null) {
                throw new IllegalStateException("Missing required status sound '" + key
                        + "' (expected in status block, preset, or templates.yml status.defaults)");
            }
            return new SoundEffect(Configs.requireSection(owner, key));
        }

        private static SoundEffect resolveOptionalSound(@Nullable ConfigurationSection ability,
                                                        @Nullable ConfigurationSection preset,
                                                        ConfigurationSection defaults,
                                                        String key) {
            ConfigurationSection owner = pickLayer(ability, preset, defaults, key);
            return owner == null ? SoundEffect.NONE : SoundEffect.optional(owner, key);
        }

        private static VisualEffect resolveOptionalVisual(@Nullable ConfigurationSection ability,
                                                          @Nullable ConfigurationSection preset,
                                                          ConfigurationSection defaults,
                                                          String key) {
            ConfigurationSection owner = pickLayer(ability, preset, defaults, key);
            return owner == null ? VisualEffect.NONE : VisualEffect.optional(owner, key);
        }

        private static @Nullable ConfigurationSection pickLayer(@Nullable ConfigurationSection ability,
                                                                @Nullable ConfigurationSection preset,
                                                                ConfigurationSection defaults,
                                                                String key) {
            if (ability != null && ability.contains(key)) return ability;
            if (preset != null && preset.contains(key)) return preset;
            if (defaults.contains(key)) return defaults;
            return null;
        }

        private static String requireValidPreset(String rawPreset) {
            String preset = rawPreset.toLowerCase(Locale.ROOT);
            if (!TemplateConfig.getConfigKeys("status.presets").contains(preset)) {
                throw new IllegalStateException("Unknown status preset '" + rawPreset
                        + "' (expected a key under templates.yml status.presets)");
            }
            return preset;
        }
    }

    /** One active status instance held by a victim. */
    public static final class ActiveEffect {
        private final Spec spec;
        private final Object source;
        private final @Nullable Hero applier;
        private final long expiresAtMs;
        private final @Nullable Location holdAnchor;
        private final @Nullable Runnable onExpire;
        private int ticksElapsed;

        private ActiveEffect(Spec spec, Object source, @Nullable Hero applier, long expiresAtMs,
                             @Nullable Location holdAnchor, @Nullable Runnable onExpire) {
            this.spec = spec;
            this.source = source;
            this.applier = applier;
            this.expiresAtMs = expiresAtMs;
            this.holdAnchor = holdAnchor;
            this.onExpire = onExpire;
        }

        public String id() { return spec.id(); }
        public Spec spec() { return spec; }
        public Object source() { return source; }
        public @Nullable Hero applier() { return applier; }
        /** Wall-clock expiry timestamp in ms, or {@code -1} when the effect has no fixed duration. */
        public long expiresAtMs() { return expiresAtMs; }
        public boolean hasDuration() { return expiresAtMs >= 0; }

        public long remainingMs() {
            if (expiresAtMs < 0) return -1L;
            return Math.max(0L, expiresAtMs - System.currentTimeMillis());
        }
    }

    private final List<ActiveEffect> effects = new ArrayList<>();
    private boolean moveLocked;
    private boolean holdLocked;

    // --- Static entry points (Hero + non-Hero victims) ---

    /**
     * Applies {@code spec} to {@code victim}, replacing any previous effect from {@code source}.
     * Works for both {@link Hero} victims and non-hero entities (practice dummies).
     */
    public static boolean applyTo(LivingEntity victim, Spec spec, Object source,
                                  @Nullable Hero applier, @Nullable Runnable onExpire) {
        Objects.requireNonNull(victim, "victim must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (!victim.isValid() || victim.isDead()) return false;

        long expiresAt = spec.durationMs() >= 0 ? System.currentTimeMillis() + spec.durationMs() : -1L;
        Location anchor = spec.hold() ? victim.getLocation().clone() : null;
        ActiveEffect effect = new ActiveEffect(spec, source, applier, expiresAt, anchor, onExpire);

        Hero victimHero = HeroManager.getHero(victim);
        if (victimHero != null) {
            if (victimHero.isRespawning() || victimHero.isEliminated()) return false;
            victimHero.status().apply(victimHero, effect);
            return true;
        }

        UUID id = victim.getUniqueId();
        List<ActiveEffect> list = NON_HERO_EFFECTS.computeIfAbsent(id, k -> new ArrayList<>());
        boolean isRefresh = list.removeIf(e -> e.source == source);
        list.add(effect);
        syncNonHeroHold(victim, list);
        if (!isRefresh) {
            playApplyFeedback(victim.getLocation(), null, effect);
        }
        return true;
    }

    /** True if {@code victim} currently has an active status applied by {@code source}. */
    public static boolean hasFrom(LivingEntity victim, Object source) {
        Hero hero = HeroManager.getHero(victim);
        if (hero != null) return hero.status().hasFromSource(source);
        List<ActiveEffect> list = NON_HERO_EFFECTS.get(victim.getUniqueId());
        if (list == null) return false;
        for (ActiveEffect e : list) {
            if (e.source == source) return true;
        }
        return false;
    }

    /** Removes every active effect applied by {@code source} across all victims without firing {@code onExpire}. */
    public static void removeAllFrom(Object source) {
        for (Hero hero : new ArrayList<>(ACTIVE_HEROES)) {
            hero.status().removeFrom(hero, source);
        }
        Iterator<Map.Entry<UUID, List<ActiveEffect>>> it = NON_HERO_EFFECTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<ActiveEffect>> entry = it.next();
            List<ActiveEffect> list = entry.getValue();
            List<ActiveEffect> removed = extractMatching(list, e -> e.source == source);
            if (!removed.isEmpty()) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living && living.isValid()) {
                    syncNonHeroHold(living, list);
                    if (!living.isDead()) {
                        Location loc = living.getLocation();
                        for (ActiveEffect e : removed) {
                            playEndFeedback(loc, null, e);
                        }
                    }
                }
                if (list.isEmpty()) it.remove();
            }
        }
    }

    /** Ends every {@code break_on_knockback} effect on {@code victim} without firing {@code onExpire}. */
    public static void onKnockbackTaken(LivingEntity victim) {
        Hero hero = HeroManager.getHero(victim);
        if (hero != null) {
            hero.status().breakOnKnockback(hero);
            return;
        }
        List<ActiveEffect> list = NON_HERO_EFFECTS.get(victim.getUniqueId());
        if (list == null) return;
        List<ActiveEffect> removed = extractMatching(list, e -> e.spec.breakOnKnockback());
        if (!removed.isEmpty()) {
            syncNonHeroHold(victim, list);
            if (list.isEmpty()) NON_HERO_EFFECTS.remove(victim.getUniqueId());
            if (victim.isValid() && !victim.isDead()) {
                Location loc = victim.getLocation();
                for (ActiveEffect e : removed) {
                    playEndFeedback(loc, null, e);
                }
            }
        }
    }

    /** True if {@code victim} currently has any active {@code hold: true} effect. */
    public static boolean isHeld(LivingEntity victim) {
        Hero hero = HeroManager.getHero(victim);
        if (hero != null) return hero.status().isHeld();
        List<ActiveEffect> list = NON_HERO_EFFECTS.get(victim.getUniqueId());
        if (list == null) return false;
        for (ActiveEffect e : list) {
            if (e.spec.hold()) return true;
        }
        return false;
    }

    /** Ticks non-hero (practice dummy) status timers, loop feedback, and holds. Called once per server tick. */
    public static void tickNonHeroEffects() {
        if (NON_HERO_EFFECTS.isEmpty()) return;
        long now = System.currentTimeMillis();
        List<Runnable> expiredCallbacks = null;

        Iterator<Map.Entry<UUID, List<ActiveEffect>>> it = NON_HERO_EFFECTS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, List<ActiveEffect>> entry = it.next();
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead()) {
                it.remove();
                continue;
            }
            List<ActiveEffect> list = entry.getValue();
            List<ActiveEffect> ended = null;
            List<ActiveEffect> looped = null;
            boolean changed = false;
            Iterator<ActiveEffect> effectIt = list.iterator();
            while (effectIt.hasNext()) {
                ActiveEffect e = effectIt.next();
                if (e.expiresAtMs >= 0 && now >= e.expiresAtMs) {
                    effectIt.remove();
                    changed = true;
                    if (ended == null) ended = new ArrayList<>();
                    ended.add(e);
                    if (e.onExpire != null) {
                        if (expiredCallbacks == null) expiredCallbacks = new ArrayList<>();
                        expiredCallbacks.add(e.onExpire);
                    }
                } else {
                    e.ticksElapsed++;
                    if (e.ticksElapsed % e.spec.feedback().loopIntervalTicks() == 0) {
                        if (looped == null) looped = new ArrayList<>();
                        looped.add(e);
                    }
                }
            }
            if (changed) syncNonHeroHold(living, list);
            Location loc = living.getLocation();
            if (ended != null) {
                for (ActiveEffect e : ended) {
                    playEndFeedback(loc, null, e);
                }
            }
            if (looped != null) {
                for (ActiveEffect e : looped) {
                    playLoopFeedback(loc, null, e);
                }
            }
            if (list.isEmpty()) {
                it.remove();
            } else {
                enforceNonHeroHold(living, list);
            }
        }

        if (expiredCallbacks != null) {
            for (Runnable callback : expiredCallbacks) {
                callback.run();
            }
        }
    }

    /** Clears all non-hero status entries without callbacks or end feedback (plugin shutdown / practice reset). */
    public static void clearAllNonHero() {
        for (Map.Entry<UUID, List<ActiveEffect>> entry : NON_HERO_EFFECTS.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living && living.isValid()) {
                AttributeInstance gravity = Objects.requireNonNull(living.getAttribute(Attribute.GRAVITY));
                gravity.setBaseValue(gravity.getDefaultValue());
            }
        }
        NON_HERO_EFFECTS.clear();
    }

    // --- Instance queries (per-Hero) ---

    public boolean blocks(Restriction restriction) {
        for (ActiveEffect e : effects) {
            Spec s = e.spec;
            boolean blocked = switch (restriction) {
                case MOVE -> s.blockMove() || s.hold();
                case JUMP -> s.blockJump() || s.hold();
                case ABILITIES -> s.blockAbilities();
                case JAB -> s.blockJab();
            };
            if (blocked) return true;
        }
        return false;
    }

    public boolean isHeld() {
        for (ActiveEffect e : effects) {
            if (e.spec.hold()) return true;
        }
        return false;
    }

    public boolean has(String id) {
        for (ActiveEffect e : effects) {
            if (e.spec.id().equals(id)) return true;
        }
        return false;
    }

    public boolean hasFromSource(Object source) {
        for (ActiveEffect e : effects) {
            if (e.source == source) return true;
        }
        return false;
    }

    public List<ActiveEffect> active() {
        return Collections.unmodifiableList(effects);
    }

    // --- Instance lifecycle (called by Hero) ---

    private void apply(Hero hero, ActiveEffect effect) {
        boolean isRefresh = effects.removeIf(e -> e.source == effect.source);
        effects.add(effect);
        ACTIVE_HEROES.add(hero);
        syncModifiers(hero);
        if (effect.spec.hold()) {
            hero.getController().setVelocity(new Vector(0, 0, 0));
        }
        if (!isRefresh && effect.applier() != hero) {
            Controller controller = hero.getController();
            playApplyFeedback(controller.getLocation(), controller, effect);
        }
    }

    public void removeFrom(Hero hero, Object source) {
        List<ActiveEffect> removed = extractMatching(effects, e -> e.source == source);
        if (removed.isEmpty()) return;
        if (effects.isEmpty()) ACTIVE_HEROES.remove(hero);
        syncModifiers(hero);
        playHeroEndFeedback(hero, removed);
    }

    private void breakOnKnockback(Hero hero) {
        List<ActiveEffect> removed = extractMatching(effects, e -> e.spec.breakOnKnockback());
        if (removed.isEmpty()) return;
        if (effects.isEmpty()) ACTIVE_HEROES.remove(hero);
        syncModifiers(hero);
        playHeroEndFeedback(hero, removed);
    }

    /** Clears all active effects on {@code hero} without invoking {@code onExpire} callbacks or end feedback. */
    public void clear(Hero hero) {
        ACTIVE_HEROES.remove(hero);
        if (effects.isEmpty() && !moveLocked && !holdLocked) return;
        effects.clear();
        syncModifiers(hero);
    }

    /** Drives effect expiry, end/loop feedback, expiry callbacks, and per-tick hold locking from {@link Hero#tick()}. */
    public void tick(Hero hero) {
        if (effects.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<ActiveEffect> ended = null;
        List<ActiveEffect> looped = null;
        List<Runnable> expiredCallbacks = null;
        boolean changed = false;

        Iterator<ActiveEffect> it = effects.iterator();
        while (it.hasNext()) {
            ActiveEffect e = it.next();
            if (e.expiresAtMs >= 0 && now >= e.expiresAtMs) {
                it.remove();
                changed = true;
                if (ended == null) ended = new ArrayList<>();
                ended.add(e);
                if (e.onExpire != null) {
                    if (expiredCallbacks == null) expiredCallbacks = new ArrayList<>();
                    expiredCallbacks.add(e.onExpire);
                }
            } else {
                e.ticksElapsed++;
                if (e.ticksElapsed % e.spec.feedback().loopIntervalTicks() == 0) {
                    if (looped == null) looped = new ArrayList<>();
                    looped.add(e);
                }
            }
        }

        if (changed) {
            if (effects.isEmpty()) ACTIVE_HEROES.remove(hero);
            syncModifiers(hero);
        }

        if (ended != null) {
            playHeroEndFeedback(hero, ended);
        }

        if (expiredCallbacks != null) {
            for (Runnable callback : expiredCallbacks) {
                if (!isHeroAliveForFeedback(hero)) break;
                callback.run();
            }
        }

        if (looped != null && isHeroAliveForFeedback(hero)) {
            Controller controller = hero.getController();
            Location loc = controller.getLocation();
            for (ActiveEffect e : looped) {
                if (e.applier() != hero && effects.contains(e)) {
                    playLoopFeedback(loc, controller, e);
                }
            }
        }

        enforceHeroHold(hero);
    }

    private static boolean isHeroAliveForFeedback(Hero hero) {
        if (hero.isEliminated() || hero.isRespawning()) return false;
        Controller c = hero.getController();
        return c != null && c.getEntity() != null && c.getEntity().isValid() && !c.getEntity().isDead();
    }

    private static void playHeroEndFeedback(Hero hero, List<ActiveEffect> removed) {
        if (!isHeroAliveForFeedback(hero)) return;
        Controller controller = hero.getController();
        Location loc = controller.getLocation();
        for (ActiveEffect e : removed) {
            if (e.applier() != hero) {
                playEndFeedback(loc, controller, e);
            }
        }
    }

    private static void playApplyFeedback(Location loc, @Nullable Controller controller, ActiveEffect effect) {
        Feedback fb = effect.spec.feedback();
        fb.applySound().play(loc, controller);
        fb.applyVisual().play(loc);
    }

    private static void playLoopFeedback(Location loc, @Nullable Controller controller, ActiveEffect effect) {
        Feedback fb = effect.spec.feedback();
        fb.loopSound().play(loc, controller);
        fb.loopVisual().play(loc);
    }

    private static void playEndFeedback(Location loc, @Nullable Controller controller, ActiveEffect effect) {
        Feedback fb = effect.spec.feedback();
        fb.endSound().play(loc, controller);
        fb.endVisual().play(loc);
    }

    private static List<ActiveEffect> extractMatching(List<ActiveEffect> list,
                                                      java.util.function.Predicate<ActiveEffect> predicate) {
        List<ActiveEffect> removed = List.of();
        Iterator<ActiveEffect> it = list.iterator();
        while (it.hasNext()) {
            ActiveEffect e = it.next();
            if (predicate.test(e)) {
                it.remove();
                if (removed.isEmpty()) removed = new ArrayList<>();
                removed.add(e);
            }
        }
        return removed;
    }

    private void syncModifiers(Hero hero) {
        Controller controller = hero.getController();
        boolean shouldLockMove = blocks(Restriction.MOVE);
        if (shouldLockMove != moveLocked) {
            moveLocked = shouldLockMove;
            if (shouldLockMove) {
                hero.getAttributes().addSpeedMultiplier(controller, MOVE_MODIFIER_KEY, 0.0);
                hero.getAttributes().addJumpStrengthMultiplier(controller, MOVE_MODIFIER_KEY, 0.0);
                if (controller instanceof BotController bot && bot.isNavigating()) {
                    bot.stopNavigation();
                }
            } else {
                hero.getAttributes().removeSpeedModifier(controller, MOVE_MODIFIER_KEY);
                hero.getAttributes().removeJumpStrengthModifier(controller, MOVE_MODIFIER_KEY);
            }
        }

        boolean shouldHold = isHeld();
        if (shouldHold != holdLocked) {
            holdLocked = shouldHold;
            if (shouldHold) {
                hero.getAttributes().addGravityMultiplier(controller, HOLD_MODIFIER_KEY, 0.0);
            } else {
                hero.getAttributes().removeGravityModifier(controller, HOLD_MODIFIER_KEY);
            }
        }
    }

    private void enforceHeroHold(Hero hero) {
        Location anchor = firstHoldAnchor(effects);
        if (anchor == null) return;
        Controller controller = hero.getController();

        controller.setVelocity(new Vector(0, 0, 0));
        Location current = controller.getLocation();
        if (current.getWorld().equals(anchor.getWorld())
                && current.distanceSquared(anchor) > HOLD_EPSILON_SQ) {
            Location snap = anchor.clone();
            snap.setYaw(current.getYaw());
            snap.setPitch(current.getPitch());
            controller.teleport(snap);
        }
    }

    private static void syncNonHeroHold(LivingEntity victim, List<ActiveEffect> list) {
        AttributeInstance gravity = Objects.requireNonNull(victim.getAttribute(Attribute.GRAVITY));
        boolean held = firstHoldAnchor(list) != null;
        gravity.setBaseValue(held ? 0.0 : gravity.getDefaultValue());
        if (held) victim.setVelocity(new Vector(0, 0, 0));
    }

    private static void enforceNonHeroHold(LivingEntity victim, List<ActiveEffect> list) {
        Location anchor = firstHoldAnchor(list);
        if (anchor == null) return;
        victim.setVelocity(new Vector(0, 0, 0));
        Location current = victim.getLocation();
        if (current.getWorld().equals(anchor.getWorld())
                && current.distanceSquared(anchor) > HOLD_EPSILON_SQ) {
            Location snap = anchor.clone();
            snap.setYaw(current.getYaw());
            snap.setPitch(current.getPitch());
            victim.teleport(snap);
        }
    }

    private static @Nullable Location firstHoldAnchor(List<ActiveEffect> list) {
        for (ActiveEffect e : list) {
            if (e.spec.hold() && e.holdAnchor != null) return e.holdAnchor;
        }
        return null;
    }
}
