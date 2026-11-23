package me.stolyy.heroes.ability.kit.bug;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

public class BugVoidBeast extends BaseAbility {
    private final SoundEffect activationSound;
    private final VisualEffect auraVisual;
    private final int auraInterval;
    private int auraTicks;
    private boolean isActive = false;

    public BugVoidBeast(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.auraVisual = new VisualEffect(this.config.getConfigurationSection("visuals.aura_visual"));
        this.auraInterval = this.config.getInt("visuals.aura_interval", 10);
    }

    @Override
    public void perform(Hero hero) {
        isActive = true;

        activationSound.play(hero.getController());
        
        BugCharms charms = findCharms(hero);
        if (charms != null) {
            charms.setUltimateActive(hero, true);
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
        
        BugCharms charms = findCharms(hero);
        if (charms != null) {
            charms.setUltimateActive(hero, false);
        }
    }

    private BugCharms findCharms(Hero hero) {
        for (Ability passive : hero.getPassiveAbilities()) {
            if (passive instanceof BugCharms) return (BugCharms) passive;
        }
        return null;
    }
}