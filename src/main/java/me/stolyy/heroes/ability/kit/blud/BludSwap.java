package me.stolyy.heroes.ability.kit.blud;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Blud PASSIVE_SWAP — cycles BludPrimary's mode: F forward, sneak+F backward
 * (Controller.isSneaking, so bots could cycle too).
 */
public class BludSwap extends BaseAbility {
    private final SoundEffect swapSound;
    private BludPrimary primary;

    public BludSwap(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.swapSound = SoundEffect.optional(this.config, "visuals.swap_sound");
    }

    @Override
    public void initialize(Hero hero) {
        this.primary = hero.findAbility(BludPrimary.class).orElse(null);
    }

    @Override
    public void perform(Hero hero) {
        if (primary == null) return;
        if (hero.getController().isSneaking()) {
            primary.retreatMode(hero);
        } else {
            primary.advanceMode(hero);
        }
        swapSound.play(hero.getController().getLocation(), hero.getController());
    }
}
