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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Single source of truth for Skin Shard reward pools, weighted drop rarity rolls, shard combining,
 * and shard consumption ({@link ShardKind#RANDOM}, {@link ShardKind#HERO_SELECT},
 * {@link ShardKind#SKIN_SELECT}).
 */
public final class SkinShards {
    public static final int COMBINE_COST = 2;

    public record PoolEntry(String heroId, HeroSkin skin) {}

    public enum Failure { NONE, NO_SHARDS, EMPTY_POOL }

    public record ConsumeResult(@Nullable PoolEntry won, Failure failure) {}

    public enum CombineRecipe {
        UPGRADE_RARITY,
        TO_HERO_SELECT,
        TO_SKIN_SELECT
    }

    public record CombineSpec(ShardKind fromKind, Rarity fromRarity, ShardKind toKind, Rarity toRarity) {}

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

    /** Groups {@link #eligiblePool(Player, Rarity)} by hero id, containing only heroes with >= 1 eligible skin. */
    public static Map<String, List<PoolEntry>> eligiblePoolByHero(Player player, Rarity rarity) {
        Map<String, List<PoolEntry>> grouped = new LinkedHashMap<>();
        for (PoolEntry entry : eligiblePool(player, rarity)) {
            grouped.computeIfAbsent(entry.heroId(), k -> new ArrayList<>()).add(entry);
        }
        return grouped;
    }

    /** Resolves a combine recipe for {@code rarity}, or {@code null} when not offered (Legendary upgrade). */
    public static @Nullable CombineSpec resolveCombine(CombineRecipe recipe, Rarity rarity) {
        return switch (recipe) {
            case UPGRADE_RARITY -> {
                Rarity next = rarity.next();
                yield next == null ? null : new CombineSpec(ShardKind.RANDOM, rarity, ShardKind.RANDOM, next);
            }
            case TO_HERO_SELECT -> new CombineSpec(ShardKind.RANDOM, rarity, ShardKind.HERO_SELECT, rarity);
            case TO_SKIN_SELECT -> new CombineSpec(ShardKind.HERO_SELECT, rarity, ShardKind.SKIN_SELECT, rarity);
        };
    }

    /** Performs an atomic 2 -> 1 shard combine on {@code player}'s stats and persists immediately. */
    public static boolean combine(Player player, CombineRecipe recipe, Rarity rarity) {
        CombineSpec spec = resolveCombine(recipe, rarity);
        if (spec == null) return false;
        PlayerStats stats = PlayerStatsManager.getStats(player);
        if (!stats.exchangeShards(spec.fromKind(), spec.fromRarity(), COMBINE_COST,
                spec.toKind(), spec.toRarity(), 1)) {
            return false;
        }
        PlayerStatsManager.saveStats(stats);
        return true;
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
        return grantAndSpend(stats, ShardKind.RANDOM, rarity, won);
    }

    /**
     * Consumes one {@link ShardKind#HERO_SELECT} shard of {@code rarity} for {@code player},
     * granting a uniformly random skin of {@code heroId} from {@link #eligiblePool}.
     */
    public static ConsumeResult consumeHeroSelect(Player player, Rarity rarity, String heroId) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        if (stats.getShardCount(ShardKind.HERO_SELECT, rarity) <= 0) {
            return new ConsumeResult(null, Failure.NO_SHARDS);
        }
        List<PoolEntry> heroPool = eligiblePool(player, rarity).stream()
                .filter(e -> e.heroId().equalsIgnoreCase(heroId))
                .toList();
        if (heroPool.isEmpty()) {
            return new ConsumeResult(null, Failure.EMPTY_POOL);
        }
        PoolEntry won = heroPool.get(ThreadLocalRandom.current().nextInt(heroPool.size()));
        return grantAndSpend(stats, ShardKind.HERO_SELECT, rarity, won);
    }

    /**
     * Consumes one {@link ShardKind#SKIN_SELECT} shard of {@code rarity} for {@code player},
     * granting the exact chosen skin if it is in {@link #eligiblePool}.
     */
    public static ConsumeResult consumeSkinSelect(Player player, Rarity rarity, String heroId, String skinId) {
        PlayerStats stats = PlayerStatsManager.getStats(player);
        if (stats.getShardCount(ShardKind.SKIN_SELECT, rarity) <= 0) {
            return new ConsumeResult(null, Failure.NO_SHARDS);
        }
        PoolEntry match = null;
        for (PoolEntry entry : eligiblePool(player, rarity)) {
            if (entry.heroId().equalsIgnoreCase(heroId) && entry.skin().getId().equalsIgnoreCase(skinId)) {
                match = entry;
                break;
            }
        }
        if (match == null) {
            return new ConsumeResult(null, Failure.EMPTY_POOL);
        }
        return grantAndSpend(stats, ShardKind.SKIN_SELECT, rarity, match);
    }

    // Grants the skin first and consumes the shard only after the grant succeeds.
    private static ConsumeResult grantAndSpend(PlayerStats stats, ShardKind kind, Rarity rarity, PoolEntry won) {
        if (!stats.grantSkin(won.heroId(), won.skin().getId())) {
            return new ConsumeResult(null, Failure.EMPTY_POOL);
        }
        stats.removeShards(kind, rarity, 1);
        PlayerStatsManager.saveStats(stats);
        return new ConsumeResult(won, Failure.NONE);
    }

    public static String formatRarity(Rarity rarity) {
        String lower = rarity.name().toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
