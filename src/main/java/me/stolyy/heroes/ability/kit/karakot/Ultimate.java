package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class Ultimate extends ActiveAbility {
    private final double damageMitigation;
    private final double primaryCooldownReductionPercent;
    private final double passiveEnergyReductionPercent;
    private final String ultSkinId;
    private HeroSkin originalSkin;

    public Ultimate(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.damageMitigation = Configs.requireDouble(this.config, "damage_mitigation");
        this.primaryCooldownReductionPercent = Configs.requireDouble(this.config, "primary_cooldown_reduction_percent");
        this.passiveEnergyReductionPercent = Configs.requireDouble(this.config, "passive_energy_reduction_percent");
        this.ultSkinId = Configs.requireString(this.config, "ult_skin");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().addDamageTakenMultiplier(this, damageMitigation);

        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().getCooldownObject().addDurationMultiplier(
                    this, 1.0 - primaryCooldownReductionPercent);
        }
        hero.findAbility(Teleport.class).ifPresent(teleport ->
                teleport.getCooldownObject().addEnergyCostMultiplier(this, passiveEnergyReductionPercent));

        originalSkin = hero.getSkin();
        HeroSkin ultSkin = CosmeticManager.getSkinById(hero, ultSkinId);
        if (ultSkin != null) {
            hero.setSkin(ultSkin);
            CosmeticManager.applyCurrentSkin(hero.getController(), hero);
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().removeDamageTakenModifier(this);

        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().getCooldownObject().removeDurationModifier(this);
        }
        hero.findAbility(Teleport.class).ifPresent(teleport ->
                teleport.getCooldownObject().removeEnergyCostModifier(this));

        if (originalSkin != null) {
            hero.setSkin(originalSkin);
            CosmeticManager.applyCurrentSkin(hero.getController(), hero);
        }
    }
}
