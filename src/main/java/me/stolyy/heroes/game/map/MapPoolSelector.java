package me.stolyy.heroes.game.map;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameMode;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamBalancer;
import me.stolyy.heroes.game.TeamColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Stateful custom-lobby map pool selector supporting Auto roster-based matching or explicit filters. */
public final class MapPoolSelector {

    public enum AutoPool {
        ONE_V_ONE("auto-1v1"),
        TWO_V_TWO("auto-2v2"),
        BOTH("auto-both");

        private final String configKey;

        AutoPool(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }

    private @Nullable MapFilter filter;

    public @Nullable MapFilter filter() {
        return filter;
    }

    /** Advances {@code Auto -> V1_ONLY -> V2_ONLY -> V1_AND_V2 -> V1_OR_V2 -> ALL -> Auto}. */
    public void cycle() {
        MapFilter[] filters = MapFilter.values();
        if (filter == null) {
            filter = filters[0];
        } else if (filter.ordinal() == filters.length - 1) {
            filter = null;
        } else {
            filter = filters[filter.ordinal() + 1];
        }
    }

    public AutoPool autoPool(Game game) {
        List<Integer> sizes = new ArrayList<>();
        for (Team team : game.getTeams().values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;
            int size = TeamBalancer.orderedMembers(game, team).size();
            if (size > 0) sizes.add(size);
        }
        int seated = sizes.stream().mapToInt(Integer::intValue).sum();
        boolean is2v2 = sizes.size() == 2 && sizes.get(0) == 2 && sizes.get(1) == 2;

        if (seated <= 2) return AutoPool.ONE_V_ONE;
        if (!is2v2 && seated <= 4) return AutoPool.BOTH;
        return AutoPool.TWO_V_TWO;
    }

    public List<GameMap> poolMaps(Game game) {
        Set<GameMap> pool = filter != null ? filter.filterMaps() : autoPoolMaps(autoPool(game));
        List<GameMap> list = new ArrayList<>(pool);
        list.sort(Comparator.comparing(GameMap::name));
        return list;
    }

    public @Nullable GameMap reroll(Game game, Random random) {
        List<GameMap> pool = poolMaps(game);
        GameMap current = game.settings().map();
        pool.removeIf(m -> current != null && m.name().equals(current.name()));
        if (pool.isEmpty()) return null;
        return pool.get(random.nextInt(pool.size()));
    }

    private Set<GameMap> autoPoolMaps(AutoPool pool) {
        return switch (pool) {
            case ONE_V_ONE -> GameMapManager.getMapsForMode(GameMode.ONE_V_ONE);
            case TWO_V_TWO -> GameMapManager.getMapsForMode(GameMode.TWO_V_TWO);
            case BOTH -> {
                Set<GameMap> both = new LinkedHashSet<>(GameMapManager.getMapsForMode(GameMode.ONE_V_ONE));
                both.retainAll(GameMapManager.getMapsForMode(GameMode.TWO_V_TWO));
                yield both;
            }
        };
    }
}
