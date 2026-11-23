package me.stolyy.heroes.ability.kit.railgunner;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;

public class Clusterbuck extends Projectile {
    private final SoundEffect activationSound;

    private final String armedItemTexture;
    private final String armedItemSkin;

    private final double kick;
    private final double kickY;

    private boolean armed = false;

    public Clusterbuck(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.armedItemTexture = me.stolyy.heroes.util.Configs.requireString(this.config, "armed_item_texture");
        this.armedItemSkin = me.stolyy.heroes.util.Configs.requireString(this.config, "armed_item_skin");
        this.kick = me.stolyy.heroes.util.Configs.requireDouble(this.config, "kick");
        this.kickY = me.stolyy.heroes.util.Configs.requireDouble(this.config, "kick_y");
    }

    @Override
    public void perform(Hero hero) {
        activationSound.play(hero.getController().getLocation(), hero.getController());
        armed = true;
        CosmeticManager.setHeldModel(hero, armedItemTexture, armedItemSkin);
        hero.setPrimaryOverride(() -> fire(hero));
        hero.getController().sendActionBar(
                Component.text("Rocket armed — right-click to fire", NamedTextColor.GOLD));
    }

    private void fire(Hero hero) {
        if (!armed) return;
        armed = false;
        restoreHeldModel(hero);
        super.perform(hero);
        applyKick(hero);
    }

    private void applyKick(Hero hero) {
        org.bukkit.entity.LivingEntity self = hero.getController().getEntity();
        org.bukkit.util.Vector back = hero.getController().getEyeLocation().getDirection().multiply(-kick);
        back.setY(back.getY() + kickY);
        self.setVelocity(self.getVelocity().add(back));
    }

    @Override
    protected void onTick(Hero hero) {
        super.onTick(hero);

        if (armed && !getCooldownObject().isInUse()) {
            armed = false;
            restoreHeldModel(hero);
            hero.clearPrimaryOverride();
        }
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        if (armed) {
            armed = false;
            restoreHeldModel(hero);
            hero.clearPrimaryOverride();
        }
    }

    private void restoreHeldModel(Hero hero) {
        CosmeticManager.setHeldModel(hero, CosmeticManager.defaultHeldTexture(hero));
    }
}
