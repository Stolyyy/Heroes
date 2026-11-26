package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.vfx.effect.ItemBuilder;

/** Optional custom-model-data coordinates applied to a settings item. */
public record ModelKey(String hero, String texture, String skin) {

    public void applyTo(ItemBuilder builder) {
        builder.modelData(hero, texture, skin);
    }
}
