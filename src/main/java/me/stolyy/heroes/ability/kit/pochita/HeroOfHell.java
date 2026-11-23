package me.stolyy.heroes.ability.kit.pochita;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.mechanics.Energy;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class HeroOfHell extends BaseAbility {
    private final String hellSkinId;
    private final double damageTakenMultiplier;
    private final double damageDealtMultiplier;
    private final double energyRegenMultiplier;
    private final SoundEffect activationSound;

    private boolean isActive = false;
    private HeroSkin originalSkin;
    private float originalRegen;

    public HeroOfHell(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.hellSkinId = this.config.getString("hell_skin");
        this.damageTakenMultiplier = this.config.getDouble("damage_taken_multiplier");
        this.damageDealtMultiplier = this.config.getDouble("damage_dealt_multiplier");
        this.energyRegenMultiplier = this.config.getDouble("energy_regen_multiplier");
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;
        activationSound.play(hero.getController().getLocation(), hero.getController());

        hero.getAttributes().setDamageMultiplier(damageTakenMultiplier);
        hero.getAttributes().setDamageDealtMultiplier(damageDealtMultiplier);

        Energy energy = hero.getEnergy();
        originalRegen = energy.getRegenPerTick();
        energy.setRegenPerTick(originalRegen * (float) energyRegenMultiplier);

        originalSkin = hero.getSkin();
        HeroSkin hellSkin = CosmeticManager.getSkinById(hero, hellSkinId);
        if (hellSkin != null) {
            hero.setSkin(hellSkin);
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

        if (hero.getPrimaryAbility() != null) {
            var primaryCd = hero.getPrimaryAbility().getCooldownObject();
            if (!primaryCd.isInUse()) primaryCd.reduceCooldown(primaryCd.getCooldownDuration());
        }
    }

    @Override
    public void onExpire(Hero hero, Location location) {
        if (!isActive) return;
        isActive = false;

        hero.getAttributes().resetDamageMultiplier();
        hero.getAttributes().resetDamageDealtMultiplier();
        hero.getEnergy().setRegenPerTick(originalRegen);

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
}
