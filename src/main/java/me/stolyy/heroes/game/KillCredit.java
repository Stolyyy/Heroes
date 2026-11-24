package me.stolyy.heroes.game;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionHero;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.progression.PlayerStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Single per-game ledger for last-hit attribution, minion-to-owner routing, and damage tallies. */
public final class KillCredit {

    /** Who last damaged a victim and with which ability ({@code null} for unattributed hits). */
    public record DamageCredit(UUID damager, @Nullable String ability) {}

    private final Game game;
    private final MatchRewards rewards;
    private final MatchAnnouncer announcer;
    private final Map<UUID, DamageCredit> lastCredit = new HashMap<>();

    KillCredit(Game game, MatchRewards rewards, MatchAnnouncer announcer) {
        this.game = game;
        this.rewards = rewards;
        this.announcer = announcer;
    }

    /** Records an ability-attributed hit from {@link Interactions}. */
    public void recordHit(UUID victim, UUID damager, String ability) {
        if (victim.equals(damager)) return;
        lastCredit.put(victim, new DamageCredit(damager, ability));
    }

    /**
     * Records a non-Interactions hit for fallback killer tracking without overwriting an
     * existing ability credit from the same damager.
     */
    public void recordFallbackDamager(UUID victim, UUID damager) {
        if (victim.equals(damager)) return;
        lastCredit.compute(victim, (k, existing) ->
                existing != null && existing.damager().equals(damager)
                        ? existing
                        : new DamageCredit(damager, null));
    }

    public @Nullable DamageCredit last(UUID victim) {
        return lastCredit.get(victim);
    }

    public void clear(UUID victim) {
        lastCredit.remove(victim);
    }

    /** Routes minions to their owner's UUID; players and bots resolve to themselves. */
    public UUID creditedId(UUID uuid) {
        Hero hero = HeroManager.getHero(uuid);
        return hero instanceof MinionHero minion ? minion.getOwnerId() : uuid;
    }

    @Nullable GamePlayer resolveGamePlayer(UUID uuid) {
        GamePlayer gp = game.getGamePlayer(uuid);
        return gp != null ? gp : game.getGamePlayer(creditedId(uuid));
    }

    public void tallyDamage(UUID attacker, UUID victim, String ability, double damage) {
        if (!(HeroManager.getHero(victim) instanceof MinionHero)) {
            GamePlayer dealer = game.getGamePlayer(creditedId(attacker));
            if (dealer != null) dealer.addDamageDealt(ability, damage);
        }
        GamePlayer taker = game.getGamePlayer(victim);
        if (taker != null) taker.addDamageTaken(damage);
    }

    public void tallyVoidDamage(UUID damager, UUID victim, double amount) {
        GamePlayer dealer = game.getGamePlayer(creditedId(damager));
        if (dealer != null) dealer.addVoidDamageDealt(amount);
        GamePlayer taker = game.getGamePlayer(victim);
        if (taker != null) taker.addVoidDamageTaken(amount);
    }

    void billVoidFall(Player victim) {
        DamageCredit credit = lastCredit.get(victim.getUniqueId());
        if (credit == null) return;
        Interactions.recordVoidDamage(
                Bukkit.getPlayer(creditedId(credit.damager())),
                credit.ability(), victim.getHealth());
        tallyVoidDamage(credit.damager(), victim.getUniqueId(), victim.getHealth());
    }

    @Nullable LivingEntity resolveFallbackKiller(UUID victimId) {
        DamageCredit credit = lastCredit.get(victimId);
        if (credit == null) return null;
        Entity e = Bukkit.getEntity(credit.damager());
        return e instanceof LivingEntity le ? le : null;
    }

    @Nullable GamePlayer recordKill(UUID rawKillerId, Player victim, @Nullable String fallbackKillerName, boolean victimLeft) {
        UUID creditId = creditedId(rawKillerId);
        Player creditPlayer = Bukkit.getPlayer(creditId);
        GamePlayer killerGp = game.getGamePlayer(creditId);
        if (killerGp != null) killerGp.addKill();

        if (creditPlayer != null && Interactions.isRealPlayer(creditPlayer)) {
            DamageCredit credit = lastCredit.get(victim.getUniqueId());
            PlayerStatsManager.getStats(creditPlayer).recordKill(credit != null ? credit.ability() : null);
            if (Interactions.isRealPlayer(victim)) {
                rewards.awardKillXp(creditPlayer);
            }
        }

        Hero killerHero = HeroManager.getHero(creditId);
        if (killerHero != null) {
            killerHero.triggerKill(victim);
        }

        if (victimLeft) {
            if (killerGp != null) announcer.broadcastLeaveKill(creditId, killerGp, victim);
        } else {
            announcer.broadcastKill(creditId, killerGp, fallbackKillerName, victim);
        }
        return killerGp;
    }

    void creditLeaveAsKill(Player victim) {
        PlayerStatsManager.getStats(victim).recordDeath();
        DamageCredit credit = lastCredit.get(victim.getUniqueId());
        if (credit == null) {
            lastCredit.remove(victim.getUniqueId());
            return;
        }
        UUID creditId = creditedId(credit.damager());
        if (game.getGamePlayer(creditId) == null) {
            lastCredit.remove(victim.getUniqueId());
            return;
        }
        recordKill(credit.damager(), victim, null, true);
        lastCredit.remove(victim.getUniqueId());
    }
}
