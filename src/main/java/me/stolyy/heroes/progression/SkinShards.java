package me.stolyy.heroes.progression;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin.Rarity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single source of truth for Skin Shard reward pools, weighted drop rarity rolls, and shard
 * consumption.
 */
public final class SkinShards {
    public record PoolEntry(String heroId, HeroSkin skin) {}

    public enum Failure { NONE, NO_SHARDS, EMPTY_POOL }

    public record ConsumeResult(@Nullable PoolEntry won, Failure failure) {}

    private SkinShards() {}

    /**
     * Computes every skin of {@code rarity} that {@code player} does not yet own, is not hidden,
     * and belongs to an unlocked, non-admin-only hero. {@code default} and {@code masterskin} have
     * null rarity and are therefore never included.
     */
    public static List<PoolEntry> eligiblePool(Player player, Rarity rarity) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        List<String> heroIds = new ArrayList<>(HeroConfig.getAvailableNames());
        heroIds.sort(Comparator.naturalOrder());

        List<PoolEntry> pool = new ArrayList<>();
        for (String heroId : heroIds) {
            if (HeroConfig.isAdminOnly(heroId)) continue;
            if (!HeroManager.isHeroUnlockedFor(player, heroId)) continue;
            for (HeroSkin skin : CosmeticManager.selectableSkins(heroId)) {
                if (skin.getRarity() != rarity) continue;
                if (stats.ownsSkin(heroId, skin.getId())) continue;
                pool.add(new PoolEntry(heroId, skin));
            }
        }
        return pool;
    }

    /** Picks a dropped shard rarity according to {@code progression.yml shards.rarity-weights}. */
    public static Rarity rollDropRarity() {
        int totalWeight = 0;
        Rarity[] rarities = Rarity.values();
        int[] weights = new int[rarities.length];
        for (int i = 0; i < rarities.length; i++) {
            int weight = ProgressionConfig.getShardRarityWeight(rarities[i]);
            if (weight < 0) {
                throw new IllegalStateException("Negative shard rarity weight for " + rarities[i] + ": " + weight);
            }
            weights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            throw new IllegalStateException("progression.yml shards.rarity-weights must sum to > 0");
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cursor = 0;
        for (int i = 0; i < rarities.length; i++) {
            cursor += weights[i];
            if (roll < cursor) return rarities[i];
        }
        return rarities[rarities.length - 1];
    }

    /**
     * Consumes one {@link ShardKind#RANDOM} shard of {@code rarity} for {@code player}, granting a
     * uniformly random skin from {@link #eligiblePool}. Changes nothing when the player lacks a
     * shard or the pool is empty.
     */
    public static ConsumeResult consumeRandom(Player player, Rarity rarity) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        if (stats.getShardCount(ShardKind.RANDOM, rarity) <= 0) {
            return new ConsumeResult(null, Failure.NO_SHARDS);
        }
        List<PoolEntry> pool = eligiblePool(player, rarity);
        if (pool.isEmpty()) {
            return new ConsumeResult(null, Failure.EMPTY_POOL);
        }

        PoolEntry won = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        stats.removeShards(ShardKind.RANDOM, rarity, 1);
        stats.grantSkin(won.heroId(), won.skin().getId());
        PlayerStatsManager.saveStats(stats);
        return new ConsumeResult(won, Failure.NONE);
    }

    public static String formatRarity(Rarity rarity) {
        String lower = rarity.name().toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
