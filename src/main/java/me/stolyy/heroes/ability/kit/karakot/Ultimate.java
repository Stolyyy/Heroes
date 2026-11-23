package me.stolyy.heroes.ability.kit.karakot;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

public class Ultimate extends BaseAbility {
    private boolean isActive = false;

    private final double damageMitigation;
    private final double primaryCooldownReductionPercent;
    private long originalPrimaryCD;
    private final double passiveEnergyReductionPercent;
    private int originalPassiveEnergy;
    private final String ultSkinId;
    private HeroSkin originalSkin;
    private final SoundEffect activationSound;

    private final VisualEffect auraVisual;
    private final int auraInterval;
    private int auraTicks;

    public Ultimate(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);

        this.damageMitigation = Configs.requireDouble(config, "damage_mitigation");
        this.primaryCooldownReductionPercent = Configs.requireDouble(config, "primary_cooldown_reduction_percent");
        this.passiveEnergyReductionPercent = Configs.requireDouble(config, "passive_energy_reduction_percent");
        this.ultSkinId = Configs.requireString(config, "ult_skin");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.auraVisual = new VisualEffect(this.config.getConfigurationSection("visuals.aura_visual"));
        this.auraInterval = this.config.getInt("visuals.aura_interval", 10);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;

        hero.getAttributes().setDamageMultiplier(damageMitigation);

        activationSound.play(hero.getController());

        if (hero.getPrimaryAbility() != null) {
            originalPrimaryCD = hero.getPrimaryAbility().getCooldownObject().getCooldownDuration();
            hero.getPrimaryAbility().getCooldownObject().setCooldownDuration((long) (originalPrimaryCD * (1.0 - primaryCooldownReductionPercent)));
        }
        if(!hero.getPassiveAbilities().isEmpty()) {
            Ability passiveSneak = findPassiveSneak(hero.getPassiveAbilities());
            if(passiveSneak != null){
                originalPassiveEnergy = passiveSneak.getCooldownObject().getEnergyCost();
                passiveSneak.getCooldownObject().setEnergyCost((int) (originalPassiveEnergy * passiveEnergyReductionPercent));
            }
        }

        originalSkin = hero.getSkin();
        HeroSkin ultSkin = CosmeticManager.getSkinById(hero, ultSkinId);
        if (ultSkin != null) {
            hero.setSkin(ultSkin);

            CosmeticManager.applyCurrentSkin(hero.getController(), hero);
        }
    }

    @Override
    protected void onTick(Hero hero) {
        if (!isActive) return;

        if (!getCooldownObject().isInUse()) {
            onExpire(hero, null);
            return;
        }

        if (++auraTicks >= auraInterval) {
            auraTicks = 0;
            auraVisual.play(hero.getController().getLocation().add(0, 1, 0));
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;
        hero.getAttributes().resetDamageMultiplier();

        if (hero.getPrimaryAbility() != null) {
            hero.getPrimaryAbility().getCooldownObject().setCooldownDuration(originalPrimaryCD);
        }
        if(!hero.getPassiveAbilities().isEmpty()) {
            Ability passiveSneak = findPassiveSneak(hero.getPassiveAbilities());
            if(passiveSneak != null){
                passiveSneak.getCooldownObject().setEnergyCost(originalPassiveEnergy);
            }
        }

        if (originalSkin != null) {
            hero.setSkin(originalSkin);
            CosmeticManager.applyCurrentSkin(hero.getController(), hero);
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        onExpire(hero, null);
    }

    private Ability findPassiveSneak(List<Ability> abilities){
        for(Ability ability : abilities) {
            if(ability.getType() == AbilityType.PASSIVE_SNEAK){
                return ability;
            }
        }
        return null;
    }
}
