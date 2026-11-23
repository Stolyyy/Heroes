package me.stolyy.heroes.physics;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.effect.ParticleBuilder;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;

public class Interactions {

    // Recursion guard — prevents damage() from re-triggering JabListener
    private static final Set<UUID> processingDamage = new java.util.HashSet<>();

    public static boolean isProcessingDamage(UUID uuid) {
        return processingDamage.contains(uuid);
    }

    // --- Named Constants ---
    private static final double WEIGHT_FACTOR = 0.125;
    private static final double WEIGHT_BASE = 0.875;
    private static final double KB_DIVISOR = 2.5;
    private static final double HEALTH_FACTOR_MIN = 0.03;
    private static final double HEALTH_FACTOR_MAX = 0.04;
    private static final double HEALTH_FACTOR_BASE = 0.06;
    private static final double HEALTH_FACTOR_KB_COEFF = 0.02;
    private static final double MAX_VELOCITY_COMPONENT = 3.9;
    // Actual Bukkit health representing the "0 HP" alive buffer state. Bukkit kills at
    // setHealth(0), so displayed-0 must map to a small positive value; 0.5 renders as 0
    // everywhere the display casts to int. See the one-hit-buffer rule in applyDamage.
    private static final double ZERO_HP_BUFFER = 0.5;
    private static final double DEFAULT_VERTICAL_KB_RATIO = 0.4;
    private static final double STEEP_PITCH_THRESHOLD = 80.0;
    private static final double LOW_PITCH_MIN = -10.0;
    private static final double LOW_PITCH_MAX = 20.0;
    private static final double STEEP_VERTICAL_DAMPEN = 0.9;

    // --- KB Direction Types ---
    public enum KBType {
        DIRECTIONAL,  // Uses a provided direction vector
        POSITIONAL,   // Attacker center → victim center
        LOCATION      // Hit location → victim center
    }

    // --- Builder ---
    public static InteractionBuilder on(LivingEntity attacker, LivingEntity victim) {
        return new InteractionBuilder(attacker, victim);
    }

    public static class InteractionBuilder {
        private final LivingEntity attacker;
        private final LivingEntity victim;

        // Core values
        private double damage = 0;
        private double knockback = 0;
        private double verticalKnockback = 0;
        private String abilityName = null;

        // KB direction
        private KBType kbType = KBType.DIRECTIONAL;
        private Vector direction = null;
        private Location hitLocation = null;

        // Magnitude modifiers
        private boolean useWeight = true;
        private boolean useHealthScaling = true;
        private boolean useMitigation = true;

        // Velocity processing
        private boolean processDirection = true;
        private boolean clamp = true;
        private boolean additive = true;
        private boolean accumulate = false;

        // Distance falloff on knockback: full strength up to falloffStart, linearly down to
        // falloffMinMult at falloffEnd and beyond. Measured attacker-to-victim.
        private double falloffStart = -1, falloffEnd = -1, falloffMinMult = 0;

        // Angle override
        private Double pitch = null;

        private InteractionBuilder(LivingEntity attacker, LivingEntity victim) {
            this.attacker = attacker;
            this.victim = victim;
        }

        // Core
        public InteractionBuilder damage(double damage) { this.damage = damage; return this; }
        public InteractionBuilder knockback(double knockback) { this.knockback = knockback; return this; }
        public InteractionBuilder verticalKnockback(double vKb) { this.verticalKnockback = vKb; return this; }
        public InteractionBuilder abilityName(String name) { this.abilityName = name; return this; }

        // KB direction
        public InteractionBuilder kbType(KBType type) { this.kbType = type; return this; }
        public InteractionBuilder direction(Vector direction) { this.direction = direction; this.kbType = KBType.DIRECTIONAL; return this; }
        public InteractionBuilder fromLocation(Location hitLocation) { this.hitLocation = hitLocation; this.kbType = KBType.LOCATION; return this; }

        // Magnitude modifiers
        public InteractionBuilder staticKnockback() { useWeight = false; useHealthScaling = false; useMitigation = false; additive = false; return this; }
        public InteractionBuilder useWeight(boolean b) { this.useWeight = b; return this; }
        public InteractionBuilder useHealthScaling(boolean b) { this.useHealthScaling = b; return this; }
        public InteractionBuilder useMitigation(boolean b) { this.useMitigation = b; return this; }

        // Velocity processing
        public InteractionBuilder processDirection(boolean b) { this.processDirection = b; return this; }
        public InteractionBuilder clamp(boolean b) { this.clamp = b; return this; }
        public InteractionBuilder additive(boolean b) { this.additive = b; return this; }
        /** Always add the computed knockback to the victim's current velocity: it slows momentum before it pushes back. */
        public InteractionBuilder accumulate() { this.accumulate = true; return this; }
        public InteractionBuilder knockbackFalloff(double startDistance, double endDistance, double minMult) {
            this.falloffStart = startDistance; this.falloffEnd = endDistance; this.falloffMinMult = minMult; return this;
        }

        // Angle override
        public InteractionBuilder pitch(double angle) { this.pitch = angle; return this; }

        public void execute() {
            // Damage MUST name the ability that dealt it: PlayerStats files damage and kills under
            // this name. It used to fall back to a sticky per-player "last ability used" field, so
            // every site that never set one filed under whatever was named last. Thrown HERE,
            // before anything mutates — an exception raised inside applyDamage would escape
            // mid-hit and skip the processingDamage finally, recursion-locking the victim forever.
            if (damage != 0 && abilityName == null) {
                throw new IllegalStateException("Interactions damage(" + damage
                        + ") with no abilityName(): " + attacker.getName() + " -> " + victim.getName());
            }

            Vector dir = switch (kbType) {
                case DIRECTIONAL -> direction != null ? direction : getDirection(attacker, victim);
                case POSITIONAL -> getDirection(attacker, victim);
                case LOCATION -> getHitDirection(hitLocation, victim);
            };

            if (!GameManager.canTarget(attacker, victim)) return;

            // Spawn protection: landing a hit forfeits the ATTACKER's own protection, and a
            // protected VICTIM takes neither damage nor knockback (the hit resolves as nothing —
            // no trigger fires, so a parry can't "succeed" against it either).
            me.stolyy.heroes.game.SpawnProtection.breakFor(attacker.getUniqueId());
            if (me.stolyy.heroes.game.SpawnProtection.isProtected(victim)) return;

            if ((knockback != 0 || verticalKnockback != 0) && applyKnockback(dir)) {
                ParticleBuilder.blood(victim);
            }
            if (damage != 0) applyDamage(damage, abilityName, attacker, victim);
        }

        /** @return whether a velocity was actually written to the victim */
        private boolean applyKnockback(Vector direction) {
            Hero hero = HeroManager.getHero(victim.getUniqueId());
            // Practice dummies have no Hero (no kit, no stat block), but eating knockback is the
            // whole point of a target dummy — it's the feedback for kb tuning. They ride the same
            // math with a fixed mid-roster weight and no mitigation. Anything else without a Hero
            // stays motionless, as before.
            boolean dummy = hero == null && me.stolyy.heroes.game.PracticeManager.isDummy(victim);
            if (hero == null && !dummy) return false;

            // Negative knockback = pull toward attacker (flip direction)
            if (knockback < 0) {
                direction = direction.clone().multiply(-1);
                knockback = Math.abs(knockback);
            }

            double weight = hero != null ? hero.getAttributes().getWeight()
                    : me.stolyy.heroes.game.PracticeManager.DUMMY_WEIGHT;
            double kbTakenMult = hero != null ? hero.getAttributes().getKnockbackMultiplier() : 1.0;
            double falloff = falloffMultiplier();
            double finalKb = calculateMagnitude(knockback, weight, kbTakenMult) * falloff;
            double finalYKb = calculateMagnitude(verticalKnockback, weight, kbTakenMult) * falloff;

            // Attacker-side knockback-dealt multiplier (A2) — the symmetric counterpart to the
            // victim's knockbackMultiplier (kb taken), layered after all other modifiers.
            Hero attackerHero = HeroManager.getHero(attacker.getUniqueId());
            if (attackerHero != null) {
                double dealtMult = attackerHero.getAttributes().getKnockbackDealtMultiplier();
                finalKb *= dealtMult;
                finalYKb *= dealtMult;
            }

            // Per-component post-multiplier skip (B18.1): when the victim's multipliers zero
            // the knockback out entirely (Parry window), the velocity write is SKIPPED so a
            // zeroed vector can't act as a micro-stun — but the trigger still fires, because
            // that trigger is how Parry detects a successful block. Mirrors the damage side,
            // where triggerDamageTaken fires before the post-multiplier <=0 return.
            if (finalKb == 0 && finalYKb == 0) {
                if (hero != null) hero.triggerKnockbackTaken(attacker, new Vector(0, 0, 0));
                return false;
            }

            Vector velocity = calculateVelocity(direction, finalKb, finalYKb);
            if (hero != null) hero.triggerKnockbackTaken(attacker, velocity);
            victim.setVelocity(velocity);
            return true;
        }

        private double falloffMultiplier() {
            if (falloffEnd < 0) return 1.0;
            double distance = attacker.getLocation().distance(victim.getLocation());
            if (distance <= falloffStart) return 1.0;
            if (distance >= falloffEnd) return falloffMinMult;
            double t = (distance - falloffStart) / (falloffEnd - falloffStart);
            return 1.0 - t * (1.0 - falloffMinMult);
        }

        private double calculateMagnitude(double kb, double weight, double kbTakenMult) {
            if (kb == 0) return 0;

            double scaled = kb / KB_DIVISOR;

            double w = 1.0;
            if (useWeight) {
                w = WEIGHT_FACTOR * weight + WEIGHT_BASE;
            }

            double healthMult = 1.0;
            if (useHealthScaling) {
                double maxHP = victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
                double missingHP = maxHP - victim.getHealth();
                double healthFactor = Math.max(HEALTH_FACTOR_MIN, Math.min(HEALTH_FACTOR_MAX, HEALTH_FACTOR_BASE - HEALTH_FACTOR_KB_COEFF * scaled));
                healthMult = 1 + healthFactor * missingHP;
            }

            double mitMult = 1.0;
            if (useMitigation) {
                mitMult = kbTakenMult;
            }

            return (scaled / w) * healthMult * mitMult;
        }

        private Vector calculateVelocity(Vector direction, double finalKb, double finalYKb) {
            Vector vel;

            if (!processDirection) {
                // Raw: use direction as-is, scale by magnitude, no normalization or pitch-angle logic
                vel = direction.clone().multiply(finalKb);
            } else {
                // Processed: normalize and apply pitch-angle logic
                if (direction.lengthSquared() < 0.0001) direction = new Vector(0, 1, 0);
                else direction = direction.clone().normalize();

                double pitchAngle = Math.toDegrees(Math.asin(direction.getY()));

                if ((pitchAngle >= LOW_PITCH_MIN && pitchAngle <= LOW_PITCH_MAX) || victim.isOnGround()) {
                    Vector horizontal = direction.clone().setY(0);
                    if (horizontal.lengthSquared() > 0.0001) horizontal.normalize();
                    vel = horizontal.multiply(finalKb);
                    vel.setY(DEFAULT_VERTICAL_KB_RATIO * finalKb);
                } else if (Math.abs(pitchAngle) > STEEP_PITCH_THRESHOLD) {
                    vel = direction.multiply(finalKb);
                    vel.setY(vel.getY() * STEEP_VERTICAL_DAMPEN);
                } else {
                    vel = direction.multiply(finalKb);
                }
            }

            // Pitch override: rotate to specified angle, preserve magnitude
            if (pitch != null) {
                double magnitude = vel.length();
                double rad = Math.toRadians(pitch);
                double horizontalMag = magnitude * Math.cos(rad);
                double verticalMag = magnitude * Math.sin(rad);

                Vector hDir = vel.clone().setY(0);
                if (hDir.lengthSquared() > 0.0001) {
                    hDir.normalize();
                    vel = hDir.multiply(horizontalMag);
                    vel.setY(verticalMag);
                } else {
                    vel = new Vector(0, verticalMag, 0);
                }
            }

            // Additive vertical KB
            if (finalYKb != 0) {
                vel.add(new Vector(0, finalYKb, 0));
            }

            if (clamp) clampComponents(vel);

            if (accumulate || (additive && vel.length() < victim.getVelocity().length())) {
                vel.add(victim.getVelocity());
            }

            return vel;
        }
    }

    // --- Static Helpers ---

    private static void clampComponents(Vector vel) {
        double hLen = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
        if (hLen > MAX_VELOCITY_COMPONENT) {
            double scale = MAX_VELOCITY_COMPONENT / hLen;
            vel.setX(vel.getX() * scale);
            vel.setZ(vel.getZ() * scale);
        }
        if (Math.abs(vel.getY()) > MAX_VELOCITY_COMPONENT) {
            vel.setY(Math.signum(vel.getY()) * MAX_VELOCITY_COMPONENT);
        }
    }

    /**
     * A real, human player. Citizens NPCs (game bots, minions, practice dummies) are
     * Player-typed entities carrying the "NPC" metadata — they must never accrue
     * PlayerStats files (each spawn is a fresh uuid, and the seeded elo used to leak
     * onto the leaderboard).
     * <p>
     * BOTH checks are required. The metadata lives on the entity, so it dies with the entity when
     * the NPC library despawns it on death — an NPC mid-teardown reads as a real player and mints a
     * stats file (~400 ghost files leaked this way). The registry check covers that window but only
     * answers for entities the library still owns, so the metadata check remains the cheap first
     * pass and the fallback when the registry is a no-op (Citizens absent).
     */
    public static boolean isRealPlayer(org.bukkit.entity.Entity entity) {
        return entity instanceof Player
                && !entity.hasMetadata("NPC")
                && !NpcServiceProvider.get().isNpcEntity(entity);
    }

    /**
     * The real player a hit should be credited to: the attacker themselves, or a minion's owner.
     * Reuses {@link me.stolyy.heroes.game.Game#creditedId} so kill credit and damage credit can never
     * disagree about who owns a minion. Returns null when nothing creditable is behind the hit.
     */
    private static Player creditedAttacker(LivingEntity attacker) {
        me.stolyy.heroes.game.Game game = GameManager.getEntityGame(attacker);
        if (game != null) {
            Player credited = org.bukkit.Bukkit.getPlayer(game.creditedId(attacker.getUniqueId()));
            if (isRealPlayer(credited)) return credited;
        }
        return isRealPlayer(attacker) ? (Player) attacker : null;
    }

    private static void applyDamage(double damage, String abilityName, LivingEntity attacker, LivingEntity victim) {
        Hero attackerHero = HeroManager.getHero(attacker.getUniqueId());
        if (attackerHero != null) {
            damage *= attackerHero.getAttributes().getDamageDealtMultiplier();
        }

        Hero victimHero = HeroManager.getHero(victim.getUniqueId());
        if (victimHero != null) {
            damage *= victimHero.getAttributes().getDamageMultiplier();
        }

        if (!Double.isFinite(damage) || damage <= 0) {
            // Fully mitigated, but the trigger STILL fires: it is how Sgt Parry detects a
            // successful block, and a pure-damage hit carries no knockback trigger to fall back
            // on. Mirrors the knockback side's zeroed-multiplier return.
            if (victimHero != null) victimHero.triggerDamageTaken(attacker, damage);
            return;
        }

        // Kill/void credit, recorded on the VICTIM's game: who last hit them and with what. This is
        // the only site that knows the ability name, and it has to survive to Game.handleDeath and
        // to GameListener's out-of-bounds void path.
        Game victimGame = GameManager.getEntityGame(victim);
        if (victimGame != null) {
            victimGame.recordDamageCredit(victim.getUniqueId(), attacker.getUniqueId(), abilityName);
        }

        // Track damage stats against the CREDITED player, so a minion's damage lands on its owner
        // rather than vanishing (a minion is an NPC "player" with no stats file of its own).
        // Note the attacker-side multiplier above deliberately stays on the minion's own hero —
        // that is the minion's stat, not the owner's.
        //
        // Gated on the attacker actually being IN a game: GameManager.canTarget short-circuits
        // practice-range hits before any game lookup, so practice damage reached here with
        // currentMode/currentHero still set from the attacker's last real match — writing green
        // hood's abilities into railgunner's rows, unbounded. Minion and bot attackers are both
        // registered in entityGames, so they still resolve.
        // Damage dealt TO a minion is not tracked either: a summon is a free, respawning target,
        // and padding a damage row off one is not a statistic.
        Player attackerPlayer = creditedAttacker(attacker);
        if (attackerPlayer != null && GameManager.getEntityGame(attacker) != null
                && !(victimHero instanceof MinionHero)) {
            PlayerStatsManager.getStats(attackerPlayer).recordDamage(abilityName, damage);
        }
        if (victimGame != null) {
            victimGame.tallyDamage(attacker.getUniqueId(), victim.getUniqueId(), abilityName, damage);
        }

        processingDamage.add(victim.getUniqueId());

        try {
            // One-hit-buffer rule: 0 HP is a legal ALIVE state. A hit that would drop the
            // victim below 1 clamps them into the buffer (displays as 0); only a damaging
            // hit taken while already in the buffer kills.
            double current = victim.getHealth();

            if (current < 1.0) {
                // Already at the buffer: this hit is lethal.
                if (victimGame != null && victim instanceof Player victimPlayer && !isRealPlayer(victim)) {
                    // An NPC-backed participant must NEVER die through vanilla. Citizens' death
                    // handler is priority LOW with ignoreCancelled=true, so it detaches the NPC
                    // before our cancel is ever seen, and EntityHumanNPC.die unconditionally
                    // schedules removePlayerImmediately 15 ticks later from NMS — no event priority
                    // can stop either. The entity vanishes and every uuid-keyed game map orphans.
                    // So: cosmetic hit for the hurt flash and damager bookkeeping, park them in the
                    // buffer, and run the death ourselves.
                    victim.damage(0.4, attacker);
                    victim.setHealth(ZERO_HP_BUFFER);
                    victimGame.handleDeath(victimPlayer, attacker);
                } else {
                    // Real players — and practice NPCs, which have no game to run the death for
                    // them — keep the vanilla path: it preserves killer attribution and routes
                    // through the normal (cancelled) death event pipeline.
                    victim.damage(current + damage + 1000, attacker);
                }
            } else {
                double newHP = current - damage;
                if (newHP < 1.0) newHP = ZERO_HP_BUFFER;

                // Cosmetic hit: hurt animation/sound + damage-event bookkeeping (last-damager
                // tracking). Never lethal here since current >= 1.0. Real HP is set below.
                victim.damage(0.4, attacker);
                victim.setHealth(newHP);

                // Minions excluded: the health objectives are keyed by entity NAME, so every hit on
                // a minion would add two permanent scoreboard entries under its generated
                // "minion_xxxxxxxx" name — and a fresh pair for every buddy that ever spawns.
                if (victim instanceof Player victimPlayer && !(victimHero instanceof MinionHero)) {
                    Game game = GameManager.getEntityGame(victimPlayer.getUniqueId());
                    if (game != null) {
                        game.getVisuals().updateHealthScore(victimPlayer, (int) newHP);
                    }
                }
            }
        } finally {
            processingDamage.remove(victim.getUniqueId());
        }

        // Fires LAST, exactly once, on every damage-applying path (lethal included) — after the
        // health write so a throwing cosmetic listener can no longer suppress real damage, and
        // after the recursion lock has released so it cannot leave the victim locked forever.
        if (victimHero != null) victimHero.triggerDamageTaken(attacker, damage);
    }

    private static Vector getDirection(LivingEntity attacker, LivingEntity victim) {
        return victim.getBoundingBox().getCenter().subtract(attacker.getBoundingBox().getCenter());
    }

    private static Vector getHitDirection(Location hitLoc, LivingEntity victim) {
        return victim.getBoundingBox().getCenter().subtract(hitLoc.toVector());
    }

    /**
     * Records void damage for stats tracking when a player is knocked into the void.
     * @param attacker The player who caused the void damage
     * @param abilityName The ability used to cause the knockback
     * @param voidDamage The amount of void damage to record
     */
    public static void recordVoidDamage(Player attacker, String abilityName, double voidDamage) {
        if (attacker == null || voidDamage <= 0 || !isRealPlayer(attacker)) return;

        PlayerStats stats = PlayerStatsManager.getStats(attacker);
        stats.recordVoidDamage(abilityName, voidDamage);
    }
}
