package me.stolyy.heroes.ability.kit.punishgers;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;

public class PunishgersSwap extends BaseAbility {
    public enum WeaponMode { MINIGUN, SHOTGUN }

    private final SoundEffect swapSound;
    private WeaponMode mode = WeaponMode.MINIGUN;
    private PunishgersWeapon weapon;

    public PunishgersSwap(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.swapSound = new SoundEffect(this.config.getConfigurationSection("visuals.swap_sound"));
    }

    @Override
    public void initialize(Hero hero) {
        if (hero.getPrimaryAbility() instanceof PunishgersWeapon w) {
            this.weapon = w;
        }
    }

    public WeaponMode getMode() {
        return mode;
    }

    @Override
    public void perform(Hero hero) {
        mode = (mode == WeaponMode.MINIGUN) ? WeaponMode.SHOTGUN : WeaponMode.MINIGUN;
        swapSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Switched to " + mode.name(), NamedTextColor.GOLD));
        if (weapon != null) {
            weapon.onModeSwapped(hero);
        }
        applyHeldModel(hero);
    }

    private void applyHeldModel(Hero hero) {
        CosmeticManager.setHeldModel(hero, mode == WeaponMode.SHOTGUN
                ? "shotgun" : CosmeticManager.defaultHeldTexture(hero));
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        mode = WeaponMode.MINIGUN;
    }

    static PunishgersSwap find(Hero hero) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive instanceof PunishgersSwap swap) return swap;
        }
        return null;
    }
}
