package me.stolyy.heroes.vfx.ui.visual;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Shared 10-cell action-bar charge/draw progress bar.
 */
public final class ProgressBar {
    private ProgressBar() {}

    public static Component actionBar(String label, NamedTextColor labelColor, double fraction) {
        double clamped = Math.max(0.0, Math.min(1.0, fraction));
        int filled = (int) Math.round(clamped * 10);
        String cells = "█".repeat(filled) + "░".repeat(10 - filled);
        NamedTextColor fillColor = clamped >= 1.0 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        return Component.text(label + " ", labelColor)
                .append(Component.text(cells, fillColor));
    }
}
