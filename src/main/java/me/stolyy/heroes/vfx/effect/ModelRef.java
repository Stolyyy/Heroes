package me.stolyy.heroes.vfx.effect;

import me.stolyy.heroes.util.Configs;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/** Resource-pack custom-model-data key tuple ({@code hero}, {@code texture}, optional {@code skin}). */
public record ModelRef(String hero, String texture, @Nullable String skin) {

    public static ModelRef parse(ConfigurationSection section) {
        return new ModelRef(
                Configs.requireString(section, "hero"),
                Configs.requireString(section, "texture"),
                section.getString("skin")
        );
    }

    /** Returns the pinned skin if configured, otherwise {@code liveSkin}, otherwise {@code "default"}. */
    public String resolveSkin(@Nullable String liveSkin) {
        if (skin != null) return skin;
        return liveSkin != null ? liveSkin : "default";
    }

    public String modelKey(@Nullable String liveSkin) {
        return (hero + "_" + texture + "_" + resolveSkin(liveSkin)).toLowerCase();
    }
}
