package me.stolyy.heroes.vfx.ui.gui.settings;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Read/write handle onto an arbitrary settings carrier (record or mutable object). */
public record Target<S>(Supplier<S> read, Consumer<UnaryOperator<S>> write) {

    public S get() {
        return read.get();
    }

    public void update(UnaryOperator<S> operator) {
        write.accept(operator);
    }
}
