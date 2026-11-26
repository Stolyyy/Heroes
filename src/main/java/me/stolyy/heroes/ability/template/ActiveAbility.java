package me.stolyy.heroes.ability.template;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Base template for timed active-phase abilities (ults/buffs whose effect runs until
 * {@code cooldown.isInUse()} ends, death, or owner loss). Guarantees symmetric activation and
 * single-shot deactivation across timeout, death, and teardown.
 */
public abstract class ActiveAbility extends BaseAbility {
    protected final SoundEffect activationSound;
    protected final VisualEffect auraVisual;
    protected final int auraIntervalTicks;

    private boolean active = false;
    private int auraTicks = 0;

    protected ActiveAbility(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.activationSound = SoundEffect.optional(this.config, "visuals.activation_sound");
        this.auraVisual = VisualEffect.optional(this.config, "visuals.aura_visual");
        this.auraIntervalTicks = Configs.orTemplateInt(this.config, "visuals.aura_interval", "active.aura_interval");
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public final void perform(Hero hero) {
        active = true;
        auraTicks = 0;
        activationSound.play(hero.getController());
        onActivate(hero);
    }

    @Override
    protected final void onTick(Hero hero) {
        if (active) {
            if (!cooldown.isInUse()) {
                onActiveExpired(hero);
                deactivate(hero);
            } else {
                if (++auraTicks >= auraIntervalTicks) {
                    auraTicks = 0;
                    auraVisual.play(hero.getController().getLocation().add(0, 1, 0), hero.getSkin().getId());
                }
                onActiveTick(hero);
            }
        }
        onAlwaysTick(hero);
    }

    protected final void deactivateNow(Hero hero) {
        if (!active) return;
        if (cooldown.isInUse()) cooldown.reset();
        deactivate(hero);
    }

    private void deactivate(Hero hero) {
        if (!active) return;
        active = false;
        auraTicks = 0;
        onDeactivate(hero);
    }

    @Override
    public final void onDeath(Hero hero) {
        super.onDeath(hero);
        deactivate(hero);
        onActiveDeath(hero);
    }

    @Override
    public final void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        deactivate(hero);
        onActiveOwnerLost(hero);
    }

    protected abstract void onActivate(Hero hero);
    protected abstract void onDeactivate(Hero hero);
    protected void onActiveTick(Hero hero) {}
    protected void onAlwaysTick(Hero hero) {}
    protected void onActiveExpired(Hero hero) {}
    protected void onActiveDeath(Hero hero) {}
    protected void onActiveOwnerLost(Hero hero) {}
}
