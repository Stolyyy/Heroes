package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;

public class GreenHoodSwap extends BaseAbility {
    public enum FireMode { NORMAL, BARRAGE }

    private final SoundEffect swapSound;
    private FireMode mode = FireMode.NORMAL;

    public GreenHoodSwap(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.swapSound = new SoundEffect(this.config.getConfigurationSection("visuals.swap_sound"));
    }

    public FireMode getMode() {
        return mode;
    }

    @Override
    public void perform(Hero hero) {
        mode = (mode == FireMode.NORMAL) ? FireMode.BARRAGE : FireMode.NORMAL;
        swapSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Arrow mode: " + mode.name(), NamedTextColor.GREEN));
    }
}
