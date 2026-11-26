package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.hero.Hero;
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

public class PearlArrow extends BaseAbility implements ArmedShot {
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
        this.armSound = new SoundEffect(Configs.requireSection(this.config, "visuals.arm_sound"));
        this.teleportSound = new SoundEffect(Configs.requireSection(this.config, "visuals.teleport_sound"));
        this.teleportVisual = new VisualEffect(Configs.requireSection(this.config, "visuals.teleport_visual"));
    }

    @Override
    public boolean isArmed() {
        return armed;
    }

    @Override
    public void disarm() {
        armed = false;
    }

    @Override
    public void perform(Hero hero) {
        armed = true;
        ArmedShot.disarmOthers(hero, this);
        armSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Pearl arrow — next shot", NamedTextColor.LIGHT_PURPLE));
    }

    @Override
    public boolean fireFromBow(Hero hero, GreenHoodBow bow, AbstractArrow arrow, Vector aim, Vector carried,
                               double charge, int overchargeTicks, double force) {
        armed = false;
        liveArrows.add(arrow.getUniqueId());
        bow.launch(arrow, aim, speedMin + force * speedScale, carried, new ArrowShot() {
            @Override
            public void onEntityHit(Hero h, LivingEntity victim, AbstractArrow a) {
                hit(h, victim)
                        .damage(pearlDamage)
                        .knockback(pearlKnockback)
                        .direction(a.getVelocity())
                        .execute();
                if (liveArrows.remove(a.getUniqueId())) {
                    teleportTo(h, victim.getLocation());
                }
                a.remove();
            }

            @Override
            public void onGroundHit(Hero h, AbstractArrow a) {
                if (liveArrows.remove(a.getUniqueId())) {
                    teleportTo(h, a.getLocation());
                }
                a.remove();
            }
        });
        return true;
    }

    private void teleportTo(Hero hero, Location landing) {
        Location current = hero.getController().getLocation();
        Location target = landing.clone();
        target.setYaw(current.getYaw());
        target.setPitch(current.getPitch());
        WallDetection.safeTeleport(hero.getController(), target);

        teleportSound.play(target, hero.getController());
        teleportVisual.play(target);
    }

    @Override
    public void onDeath(Hero hero) {
        disarm();
        liveArrows.clear();
        super.onDeath(hero);
    }
}
