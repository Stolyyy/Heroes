package me.stolyy.heroes.vfx.ui.gui.settings;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Field accessor pair over a settings state {@code S} with value {@code V}. */
public record Lens<S, V>(Function<S, V> get, BiFunction<S, V, S> set) {

    /** Adapts an in-place setter onto a lens by returning the mutated target. */
    public static <S, V> Lens<S, V> mutable(Function<S, V> get, BiConsumer<S, V> set) {
        return new Lens<>(get, (state, value) -> {
            set.accept(state, value);
            return state;
        });
    }
}
