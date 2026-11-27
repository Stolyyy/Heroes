package me.stolyy.heroes.hero.mechanics;

import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.util.TemplateConfig;
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
 * Per-hero status effect registry (stun, pin, root, disable, and custom toggle combinations).
 *
 * <p>Abilities apply effects through {@link #applyTo} so hero and non-hero targets (practice dummies)
 * share one entry point. Restrictions are the union of all active effects. Effects are cleared
 * without expiry callbacks on death, respawn, elimination, {@link Hero#cleanup()}, knockback break,
 * or explicit removal via {@link #removeAllFrom}.
 */
public final class StatusEffects {
    private static final double HOLD_EPSILON_SQ = 0.01 * 0.01;
    private static final Object MOVE_MODIFIER_KEY = new Object();
    private static final Object HOLD_MODIFIER_KEY = new Object();

    // Active hero instances currently carrying at least one effect, for O(active) removeAllFrom.
    private static final Set<Hero> ACTIVE_HEROES = ConcurrentHashMap.newKeySet();
    // Non-hero victims (practice dummies): no inputs to block, but timers, hold, knockback break,
    // and onExpire callbacks run identically so finishers work in practice.
    private static final Map<UUID, List<ActiveEffect>> NON_HERO_EFFECTS = new HashMap<>();

    public enum Restriction {
        MOVE,
        JUMP,
        ABILITIES,
        JAB
    }

    /**
     * Immutable specification for a status effect read from a preset in {@code templates.yml}
     * with optional per-ability toggle overrides.
     */
    public record Spec(
            String id,
            long durationMs,
            boolean move,
            boolean jump,
            boolean abilities,
            boolean jab,
            boolean hold,
            boolean breakOnKnockback
    ) {
        /** Reads a required {@code status:} section ({@code id}, {@code duration}, plus optional toggle overrides). */
        public static Spec fromConfig(ConfigurationSection parent) {
            ConfigurationSection section = Configs.requireSection(parent, "status");
            String id = requireValidPreset(Configs.requireString(section, "id"));
            long durationMs = Math.round(Configs.requireDouble(section, "duration") * 1000.0);
            String base = "status.presets." + id + ".";
            return new Spec(
                    id,
                    durationMs,
                    Configs.orTemplateBoolean(section, "move", base + "move"),
                    Configs.orTemplateBoolean(section, "jump", base + "jump"),
                    Configs.orTemplateBoolean(section, "abilities", base + "abilities"),
                    Configs.orTemplateBoolean(section, "jab", base + "jab"),
                    Configs.orTemplateBoolean(section, "hold", base + "hold"),
                    Configs.orTemplateBoolean(section, "break_on_knockback", base + "break_on_knockback")
            );
        }

        /** Loads a preset from {@code templates.yml} with no fixed duration (must be removed explicitly). */
        public static Spec preset(String presetId) {
            return preset(presetId, -1L);
        }

        /** Loads a preset from {@code templates.yml} with an explicit duration in milliseconds ({@code < 0} = indefinite). */
        public static Spec preset(String presetId, long durationMs) {
            String id = requireValidPreset(presetId);
            String base = "status.presets." + id + ".";
            return new Spec(
                    id,
                    durationMs,
                    TemplateConfig.requireBoolean(base + "move"),
                    TemplateConfig.requireBoolean(base + "jump"),
                    TemplateConfig.requireBoolean(base + "abilities"),
                    TemplateConfig.requireBoolean(base + "jab"),
                    TemplateConfig.requireBoolean(base + "hold"),
                    TemplateConfig.requireBoolean(base + "break_on_knockback")
            );
        }

        private static String requireValidPreset(String rawId) {
            String id = rawId.toLowerCase(Locale.ROOT);
            if (!TemplateConfig.getConfigKeys("status.presets").contains(id)) {
                throw new IllegalStateException("Unknown status preset '" + rawId
                        + "' (expected a key under templates.yml status.presets)");
            }
            return id;
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
        list.removeIf(e -> e.source == source);
        list.add(effect);
        syncNonHeroHold(victim, list);
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
            if (list.removeIf(e -> e.source == source)) {
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof LivingEntity living && living.isValid()) {
                    syncNonHeroHold(living, list);
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
        if (list.removeIf(e -> e.spec.breakOnKnockback())) {
            syncNonHeroHold(victim, list);
            if (list.isEmpty()) NON_HERO_EFFECTS.remove(victim.getUniqueId());
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

    /** Ticks non-hero (practice dummy) status timers and holds. Called once per server tick. */
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
            boolean changed = false;
            Iterator<ActiveEffect> effectIt = list.iterator();
            while (effectIt.hasNext()) {
                ActiveEffect e = effectIt.next();
                if (e.expiresAtMs >= 0 && now >= e.expiresAtMs) {
                    effectIt.remove();
                    changed = true;
                    if (e.onExpire != null) {
                        if (expiredCallbacks == null) expiredCallbacks = new ArrayList<>();
                        expiredCallbacks.add(e.onExpire);
                    }
                }
            }
            if (changed) syncNonHeroHold(living, list);
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

    /** Clears all non-hero status entries without callbacks (plugin shutdown / practice reset). */
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
                case MOVE -> !s.move() || s.hold();
                case JUMP -> !s.jump() || s.hold();
                case ABILITIES -> !s.abilities();
                case JAB -> !s.jab();
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
        effects.removeIf(e -> e.source == effect.source);
        effects.add(effect);
        ACTIVE_HEROES.add(hero);
        syncModifiers(hero);
        if (effect.spec.hold()) {
            hero.getController().setVelocity(new Vector(0, 0, 0));
        }
    }

    public void removeFrom(Hero hero, Object source) {
        if (!effects.removeIf(e -> e.source == source)) return;
        if (effects.isEmpty()) ACTIVE_HEROES.remove(hero);
        syncModifiers(hero);
    }

    private void breakOnKnockback(Hero hero) {
        if (!effects.removeIf(e -> e.spec.breakOnKnockback())) return;
        if (effects.isEmpty()) ACTIVE_HEROES.remove(hero);
        syncModifiers(hero);
    }

    /** Clears all active effects on {@code hero} without invoking {@code onExpire} callbacks. */
    public void clear(Hero hero) {
        ACTIVE_HEROES.remove(hero);
        if (effects.isEmpty() && !moveLocked && !holdLocked) return;
        effects.clear();
        syncModifiers(hero);
    }

    /** Drives effect expiry, expiry callbacks, and per-tick hold locking from {@link Hero#tick()}. */
    public void tick(Hero hero) {
        if (effects.isEmpty()) return;

        long now = System.currentTimeMillis();
        List<Runnable> expiredCallbacks = null;
        boolean changed = false;

        Iterator<ActiveEffect> it = effects.iterator();
        while (it.hasNext()) {
            ActiveEffect e = it.next();
            if (e.expiresAtMs >= 0 && now >= e.expiresAtMs) {
                it.remove();
                changed = true;
                if (e.onExpire != null) {
                    if (expiredCallbacks == null) expiredCallbacks = new ArrayList<>();
                    expiredCallbacks.add(e.onExpire);
                }
            }
        }

        if (changed) {
            if (effects.isEmpty()) ACTIVE_HEROES.remove(hero);
            syncModifiers(hero);
        }

        if (expiredCallbacks != null) {
            for (Runnable callback : expiredCallbacks) {
                if (hero.isEliminated() || hero.isRespawning()) break;
                Controller c = hero.getController();
                if (c == null || c.getEntity() == null || !c.getEntity().isValid() || c.getEntity().isDead()) break;
                callback.run();
            }
        }

        enforceHeroHold(hero);
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
