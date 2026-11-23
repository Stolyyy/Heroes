package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.listener.GreenHoodListener;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.physics.WallDetection;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PearlArrow extends BaseAbility {
    private final double speedMin;
    private final double speedScale;
    private final double pearlDamage;
    private final double pearlKnockback;
    private final SoundEffect armSound;
    private final SoundEffect teleportSound;
    private final VisualEffect teleportVisual;

    private boolean armed;
    private final Set<UUID> liveArrows = new HashSet<>();

    public PearlArrow(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.speedMin = Configs.requireDouble(this.config, "speed_min");
        this.speedScale = Configs.requireDouble(this.config, "speed_scale");
        this.pearlDamage = Configs.requireDouble(this.config, "pearl_damage");
        this.pearlKnockback = Configs.requireDouble(this.config, "pearl_knockback");
        this.armSound = new SoundEffect(this.config.getConfigurationSection("visuals.arm_sound"));
        this.teleportSound = new SoundEffect(this.config.getConfigurationSection("visuals.teleport_sound"));
        this.teleportVisual = new VisualEffect(this.config.getConfigurationSection("visuals.teleport_visual"));
    }

    public double launchSpeed(double force) {
        return speedMin + force * speedScale;
    }

    @Override
    public void perform(Hero hero) {
        armed = true;
        GreenHoodListener.disarmOthers(hero, this);
        armSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Pearl arrow — next shot", NamedTextColor.LIGHT_PURPLE));
    }

    public boolean isArmed() {
        return armed;
    }

    public void disarm() {
        armed = false;
    }

    /** Takes over the bow's own arrow from the shoot event; see {@link GreenHoodListener#onShoot}. */
    public void fire(AbstractArrow arrow, Vector aim, Vector carried, double speed) {
        armed = false;
        GreenHoodListener.aim(arrow, aim, speed, carried, "pearl", pearlDamage);
        liveArrows.add(arrow.getUniqueId());
    }

    public void onVictimContact(Hero hero, LivingEntity victim, AbstractArrow arrow) {
        hero.triggerAbilityHit(this, victim);
        Interactions.on(hero.getController().getEntity(), victim)
                .abilityName(getName())
                .damage(pearlDamage)
                .knockback(pearlKnockback)
                .direction(arrow.getVelocity())
                .execute();
        if (liveArrows.remove(arrow.getUniqueId())) {
            teleportTo(hero, victim.getLocation());
        }
    }

    public void onGroundContact(Hero hero, AbstractArrow arrow) {
        if (liveArrows.remove(arrow.getUniqueId())) {
            teleportTo(hero, arrow.getLocation());
        }
    }

    private void teleportTo(Hero hero, Location landing) {
        Location current = hero.getController().getLocation();
        Location target = landing.clone();
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());
        hero.getController().teleport(target);

        LivingEntity entity = hero.getController().getEntity();
        if (WallDetection.isTrapped(entity)) {
            Vector out = WallDetection.escapeOffset(entity, 3.0, 0.1);
            if (out != null) entity.teleport(entity.getLocation().add(out));
        }

        teleportSound.play(target, hero.getController());
        teleportVisual.play(target);
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        armed = false;
        liveArrows.clear();
    }
}
