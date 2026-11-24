package me.stolyy.heroes.game.map;

import me.stolyy.heroes.game.GameMode;

import java.util.LinkedHashSet;
import java.util.Set;

/** Mode filters over the registered {@link GameMap} catalog. */
public enum MapFilter {
    V1_ONLY("1v1-only") {
        @Override
        public Set<GameMap> filterMaps() {
            return GameMapManager.getMapsForMode(GameMode.ONE_V_ONE);
        }
    },
    V2_ONLY("2v2-only") {
        @Override
        public Set<GameMap> filterMaps() {
            return GameMapManager.getMapsForMode(GameMode.TWO_V_TWO);
        }
    },
    V1_AND_V2("1v1-and-2v2") {
        @Override
        public Set<GameMap> filterMaps() {
            Set<GameMap> result = new LinkedHashSet<>(GameMapManager.getMapsForMode(GameMode.ONE_V_ONE));
            result.retainAll(GameMapManager.getMapsForMode(GameMode.TWO_V_TWO));
            return result;
        }
    },
    V1_OR_V2("1v1-or-2v2") {
        @Override
        public Set<GameMap> filterMaps() {
            Set<GameMap> result = new LinkedHashSet<>(GameMapManager.getMapsForMode(GameMode.ONE_V_ONE));
            result.addAll(GameMapManager.getMapsForMode(GameMode.TWO_V_TWO));
            return result;
        }
    },
    ALL("all") {
        @Override
        public Set<GameMap> filterMaps() {
            Set<GameMap> all = new LinkedHashSet<>();
            for (GameMode mode : GameMode.values()) {
                all.addAll(GameMapManager.getMapsForMode(mode));
            }
            return all;
        }
    };

    private final String configKey;

    MapFilter(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public abstract Set<GameMap> filterMaps();
}
