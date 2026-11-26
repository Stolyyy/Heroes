package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.ActiveAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import org.bukkit.configuration.ConfigurationSection;

public class HeroOfHell extends ActiveAbility {
    private final String hellSkinId;
    private final double damageTakenMultiplier;
    private final double damageDealtMultiplier;
    private final double energyRegenMultiplier;

    private HeroSkin originalSkin;

    public HeroOfHell(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.hellSkinId = Configs.requireString(this.config, "hell_skin");
        this.damageTakenMultiplier = Configs.requireDouble(this.config, "damage_taken_multiplier");
        this.damageDealtMultiplier = Configs.requireDouble(this.config, "damage_dealt_multiplier");
        this.energyRegenMultiplier = Configs.requireDouble(this.config, "energy_regen_multiplier");
    }

    @Override
    protected void onActivate(Hero hero) {
        hero.getAttributes().addDamageTakenMultiplier(this, damageTakenMultiplier);
        hero.getAttributes().addDamageDealtMultiplier(this, damageDealtMultiplier);
        hero.getEnergy().addRegenMultiplier(this, energyRegenMultiplier);

        originalSkin = hero.getSkin();
        HeroSkin hellSkin = CosmeticManager.getSkinById(hero, hellSkinId);
        if (hellSkin != null) {
            hero.setSkin(hellSkin);
            CosmeticManager.applyCurrentSkin(hero.getController(), hero);
        }
    }

    @Override
    protected void onActiveTick(Hero hero) {
        if (hero.getPrimaryAbility() != null) {
            var primaryCd = hero.getPrimaryAbility().getCooldownObject();
            if (!primaryCd.isInUse()) primaryCd.reduceCooldown(primaryCd.getCooldownDuration());
        }
    }

    @Override
    protected void onDeactivate(Hero hero) {
        hero.getAttributes().removeDamageTakenModifier(this);
        hero.getAttributes().removeDamageDealtModifier(this);
        hero.getEnergy().removeRegenModifier(this);

        if (originalSkin != null) {
            hero.setSkin(originalSkin);
            CosmeticManager.applyCurrentSkin(hero.getController(), hero);
        }
    }
}
