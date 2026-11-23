package me.stolyy.heroes.hero.minion;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.HeroStats;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.ai.BotAI;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.MinionBodyDisplay;
import me.stolyy.heroes.hero.mechanics.DoubleJump;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.npc.NpcHandle;
import me.stolyy.heroes.npc.NpcServiceProvider;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;

/**
 * A minion is a mostly-independent "mini hero": it owns its own controller, AI, and per-tick
 * behaviour, and resolves its game/team from fields the owner injects ONCE at {@link #spawn}.
 * It keeps a reference to its owner only for relationship behaviour (following, sounds), not for
 * re-deriving game/team membership on every lookup.
 */
public class MinionHero extends Hero {
    private final Hero owner;
    private final UUID ownerId;

    // Relationship injected once at spawn — the minion answers "what game/team am I on" from
    // these fields instead of walking back through the owner on every lookup.
    private UUID entityId;
    private Game game;
    private TeamColor teamColor;

    private boolean invincible = false;
    private boolean despawned = false;
    private double grantsCharge;

    // Full-body visual (yml body: opt-in) — an ITEM_DISPLAY passenger replacing the worn helmet.
    private MinionBodyDisplay bodyDisplay;

    public MinionHero(String name, HeroStats stats, DoubleJump mobility,
                      Ability primary, Ability secondary, Ability smash,
                      List<Ability> passives, Hero owner) {
        super(name, stats, mobility, primary, secondary, smash, passives);
        this.owner = owner;
        // A minion is only ever built from a live ability on a controlled hero, so the owner and its
        // controller both exist here. Resolving eagerly makes ownerId non-null for its whole life,
        // which is what lets kill/damage credit route through it without a guard at every site.
        this.ownerId = owner.getController().getUniqueId();
    }

    /** See templates.yml {@code minion.grants_charge}. */
    public double grantsCharge() { return grantsCharge; }
    public void setGrantsCharge(double grantsCharge) { this.grantsCharge = grantsCharge; }

    /**
     * Spawns the minion's NPC body, wires its AI, and injects its game/team relationship from the
     * owner. The minion is never spawned without an AI (passing it here removes the old
     * "forgot to setAI -> silently frozen" failure mode).
     */
    public void spawn(Location location, BotAI ai) {
        NpcHandle handle = NpcServiceProvider.get().create(
                "minion_" + UUID.randomUUID().toString().substring(0, 8), location);
        if (handle == null) {
            Heroes.getInstance().getLogger().severe(
                    "Minion '" + getName() + "' could not be created by the NPC service.");
            return;
        }

        BotController controller = new BotController(handle);
        setController(controller);

        // The body must be resolved BEFORE any stat application: resetStats/applyAttributes
        // dereference the entity (Attributes.setMaxHealth -> controller.getEntity().getAttribute),
        // so an unusable body throws there instead of reaching a later null check.
        LivingEntity entity = controller.getEntity();
        if (!(entity instanceof org.bukkit.entity.Player minionPlayer)) {
            Heroes.getInstance().getLogger().severe(
                    "Minion '" + getName() + "' spawned without a usable player body; removing NPC.");
            NpcServiceProvider.get().remove(handle.id());
            return;
        }

        resetStats(controller);
        applyAttributes(controller);
        // applyAttributes only writes the MAX_HEALTH attribute; without this the minion keeps
        // vanilla 20 current HP against its (much smaller) configured max.
        controller.healToFull();
        // The cosmetic 0.4 damage that drives the hurt animation is swallowed by the default
        // 20-tick invulnerability window.
        minionPlayer.setMaximumNoDamageTicks(1);

        // Combat NPCs must be damageable/movable — Citizens NPCs are protected by default.
        NpcServiceProvider.get().setProtected(handle, false);

        // Make entity invisible (only equipment will be visible)
        minionPlayer.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
        // Hitbox.isValidTarget only accepts Player victims in ADVENTURE (the same rule
        // GameBotNpc.spawn and PracticeManager's dummies satisfy) — Citizens force-spawns NPCs
        // in the server default mode, which is why no hitbox could ever land on a minion.
        minionPlayer.setGameMode(org.bukkit.GameMode.ADVENTURE);

        // Apply equipment from skin if configured; the piece name (minion name, normalized)
        // lets the owner's skin re-texture this specific piece (B21.2).
        String pieceName = getName().toLowerCase().replace(" ", "");
        CosmeticManager.applyMinion(controller, this, pieceName);

        // Full-body display (yml body: opt-in): applyMinion skipped the helmet for these; the
        // model rides the NPC as an ITEM_DISPLAY passenger instead (scale/anchor/yaw from yml).
        org.bukkit.configuration.ConfigurationSection bodyConfig =
                CosmeticManager.resolveMinionBody(this, pieceName);
        if (bodyConfig != null) {
            bodyDisplay = new MinionBodyDisplay(minionPlayer, bodyConfig, getName());
        }

        NpcServiceProvider.get().setShowInTab(handle, false);
        // Minions are invisibility-potioned bodies wearing equipment; a floating name over an
        // invisible entity reads wrong. (setShowInTab used to hide the nameplate as a side effect.)
        NpcServiceProvider.get().setNameplateVisible(handle, false);

        // Capture identity once so registry keys never drift if the live entity later vanishes.
        this.entityId = controller.getUniqueId();

        // Owner injects the relationship ONCE: resolve and store the game/team now so the minion
        // never re-derives them from the owner on every targeting/team lookup. A minion summoned
        // outside a game (practice range) legitimately has none.
        this.game = GameManager.getEntityGame(ownerId);
        if (game != null) {
            Team ownerTeam = game.playerTeam(ownerId);
            this.teamColor = ownerTeam != null ? ownerTeam.color() : null;
            GameManager.registerEntity(entityId, game);
        }

        // Register with the framework so the central tick loop drives this minion's AI,
        // and so HeroManager / Interactions / GameManager lookups resolve by entity UUID.
        HeroManager.register(entityId, this);
        MinionManager.register(this);

        // Wire the AI last, after registration, so onSpawn can rely on lookups resolving.
        controller.setAI(ai);
        ai.onSpawn(this, controller);

        // Teammate outlines + scoreboard-team colour for the fresh minion — both syncs are
        // roster-driven and would never see it otherwise. Practice-range minions have no game.
        if (game != null) game.onMinionSpawned();
    }

    @Override
    public void tick() {
        super.tick();

        // Citizens re-creates the NPC entity on chunk churn, which drops everything spawn() set up:
        // the entity comes back in the server default gamemode, visible, protected, with vanilla
        // i-frames. Gamemode is the change detector — all four revert together, so the normal path
        // costs one comparison.
        BotController controller = (BotController) getController();
        if (controller.getEntity() instanceof org.bukkit.entity.Player minionPlayer
                && minionPlayer.getGameMode() != org.bukkit.GameMode.ADVENTURE) {
            minionPlayer.setGameMode(org.bukkit.GameMode.ADVENTURE);
            minionPlayer.addPotionEffect(new PotionEffect(
                    PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
            minionPlayer.setMaximumNoDamageTicks(1);
            NpcServiceProvider.get().setProtected(controller.getNpcHandle(), false);
        }

        if (bodyDisplay != null) bodyDisplay.tick();
    }

    public void despawn() {
        // Minion death defers its despawn by a tick, so despawnForGame at game end can reach the
        // same minion in between — the guard keeps the teardown below (and cleanup) exactly-once.
        if (despawned) return;
        despawned = true;

        // Every other teardown path in the codebase runs Hero.cleanup(); without it a minion's
        // abilities never receive onDeath/onOwnerLost and its event bus is never cleared.
        cleanup();

        MinionManager.unregister(this);
        if (bodyDisplay != null) {
            bodyDisplay.remove();
            bodyDisplay = null;
        }
        if (entityId != null) {
            GameManager.unregisterEntity(entityId);
            HeroManager.unregister(entityId);
        }
        if (getController() instanceof BotController bot) {
            NpcHandle handle = bot.getNpcHandle();
            if (handle != null) {
                NpcServiceProvider.get().remove(handle.id());
            }
            if (bot.getAI() != null) {
                bot.getAI().cleanup();
            }
        }
    }

    public Hero getOwner() { return owner; }

    /** The full-body display when this minion opted in via body:, else null (helmet-rendered). */
    public MinionBodyDisplay getBodyDisplay() { return bodyDisplay; }

    /** The summoning player/bot. Never null — this is who a minion's kills and damage credit to. */
    public UUID getOwnerId() { return ownerId; }
    public UUID getEntityId() { return entityId; }
    public Game getGame() { return game; }

    /**
     * The side this minion fights for, re-derived live instead of read from the spawn-time
     * snapshot. That snapshot froze whatever the owner's team was at summon time: an owner already
     * on SPECTATOR (eliminated) produced a null/SPECTATOR color, which made the minion permanently
     * unhittable AND permanently invalid as a victim for its whole life. {@code combatColor} maps
     * an eliminated owner back to the color they started the round on. The field stays as the
     * practice-range fallback, where there is no game to ask.
     */
    public TeamColor getTeamColor() { return game != null ? game.combatColor(ownerId) : teamColor; }

    public void playSoundToOwner(Sound sound, float volume, float pitch) {
        if (owner != null && owner.getController() != null && owner.getController().isPlayer()) {
            owner.getController().playSound(sound, volume, pitch);
        }
    }

    public void setInvincible(boolean invincible) { this.invincible = invincible; }
    public boolean isInvincible() { return invincible; }
}
