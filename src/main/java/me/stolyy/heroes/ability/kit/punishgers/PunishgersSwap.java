package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;

public class PunishgersSwap extends BaseAbility {
    private final SoundEffect swapSound;
    private PunishgersWeapon weapon;

    public PunishgersSwap(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.swapSound = new SoundEffect(Configs.requireSection(this.config, "visuals.swap_sound"));
    }

    @Override
    public void initialize(Hero hero) {
        this.weapon = hero.findAbility(PunishgersWeapon.class).orElse(null);
    }

    @Override
    protected boolean canUse(Hero hero) {
        return weapon != null && !weapon.isSwapping();
    }

    @Override
    public void perform(Hero hero) {
        weapon.swap(hero);
        swapSound.play(hero.getController().getLocation(), hero.getController());
        String modeName = weapon.isShotgun()
                ? PunishgersWeapon.WeaponMode.SHOTGUN.name()
                : PunishgersWeapon.WeaponMode.MINIGUN.name();
        hero.getController().sendActionBar(Component.text("Switched to " + modeName, NamedTextColor.GOLD));
    }
}
