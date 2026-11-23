package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.physics.Hitbox;
import me.stolyy.heroes.physics.Interactions;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SkyfallVolley extends BaseAbility {
    private final int shots;
    private final int drawTimeTicks;
    private final int windowTicks;
    private final int liftTicks;
    private final double liftSpeed;
    private final double range;
    private final double beamRadius;
    private final double drawDamage;
    private final long drawHitIntervalMs;
    private final double burstDamage;
    private final double burstKnockback;
    private final double burstRadius;
    private final double beamSpacing;
    private final double beamVisualStartOffset;

    private final SoundEffect activationSound;
    private final SoundEffect burstSound;
    private final SoundEffect chargeLostSound;
    private final VisualEffect drawVisual;
    private final VisualEffect burstVisual;

    private boolean channeling;
    private int shotsLeft;
    private int liftTicksLeft;
    private int windowTicksLeft;
    private int drawTicks = -1;
    private final Map<UUID, Long> drawHitTimestamps = new HashMap<>();

    private double storedDamageTakenMultiplier = 1.0;
    private double storedKnockbackTakenMultiplier = 1.0;

    public SkyfallVolley(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.shots = Configs.requireInt(this.config, "shots");
        this.drawTimeTicks = Configs.requireInt(this.config, "draw_time_ticks");
        this.windowTicks = Configs.requireInt(this.config, "shot_window_ticks");
        this.liftTicks = Configs.requireInt(this.config, "lift_ticks");
        this.liftSpeed = Configs.requireDouble(this.config, "lift_speed");
        this.range = Configs.requireDouble(this.config, "range");
        this.beamRadius = Configs.requireDouble(this.config, "beam_radius");
        this.drawDamage = Configs.requireDouble(this.config, "draw_damage");
        this.drawHitIntervalMs = Configs.requireInt(this.config, "draw_hit_interval_ticks") * 50L;
        this.burstDamage = Configs.requireDouble(this.config, "burst_damage");
        this.burstKnockback = Configs.requireDouble(this.config, "burst_knockback");
        this.burstRadius = Configs.requireDouble(this.config, "burst_radius");
        this.beamSpacing = Configs.requireDouble(this.config, "visuals.beam_spacing");
        this.beamVisualStartOffset = Configs.requireDouble(this.config, "visuals.beam_visual_start_offset");

        this.activationSound = new SoundEffect(this.config.getConfigurationSection("visuals.activation_sound"));
        this.burstSound = new SoundEffect(this.config.getConfigurationSection("visuals.burst_sound"));
        this.chargeLostSound = new SoundEffect(this.config.getConfigurationSection("visuals.charge_lost_sound"));
        this.drawVisual = new VisualEffect(this.config.getConfigurationSection("visuals.draw_visual"));
        this.burstVisual = new VisualEffect(this.config.getConfigurationSection("visuals.burst_visual"));
    }

    public boolean isChanneling() {
        return channeling;
    }

    @Override
    public boolean canExecute(Hero hero) {
        if (channeling) return false;
        return super.canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        channeling = true;
        shotsLeft = shots;
        liftTicksLeft = liftTicks;
        windowTicksLeft = windowTicks;
        drawTicks = -1;
        drawHitTimestamps.clear();

        storedDamageTakenMultiplier = hero.getAttributes().getDamageMultiplier();
        storedKnockbackTakenMultiplier = hero.getAttributes().getKnockbackMultiplier();
        hero.getAttributes().setDamageMultiplier(0);
        hero.getAttributes().setKnockbackMultiplier(0);

        activationSound.play(hero.getController().getLocation(), hero.getController());
    }

    @Override
    protected void onTick(Hero hero) {
        if (!channeling) return;
        LivingEntity self = hero.getController().getEntity();
        if (self == null) {
            endChannel(hero);
            return;
        }

        if (liftTicksLeft > 0) {
            liftTicksLeft--;
            self.setVelocity(new Vector(0, liftSpeed, 0));
        } else {
            self.setVelocity(new Vector(0, 0, 0));
        }

        boolean drawingNow = self instanceof Player p
                && p.isHandRaised()
                && p.getInventory().getItemInMainHand().getType() == Material.BOW;

        if (drawTicks < 0) {
            if (drawingNow) {
                drawTicks = 0;
            } else {
                windowTicksLeft--;
                hero.getController().sendActionBar(Component.text(
                        String.format("Draw within %.1fs", Math.max(0, windowTicksLeft) / 20.0),
                        NamedTextColor.GOLD));
                if (windowTicksLeft <= 0) {
                    forfeitCharge(hero);
                }
                return;
            }
        }

        if (!drawingNow) {
            drawTicks = -1;
            return;
        }

        drawTicks++;
        Location eye = hero.getController().getEyeLocation();
        Vector direction = eye.getDirection();
        paintBeam(eye, direction);
        sweepDrawBeam(hero, eye, direction);
        sendDrawBar(hero);

        if (drawTicks >= drawTimeTicks) {
            fireBurst(hero, eye, direction);
        }
    }

    private void paintBeam(Location eye, Vector direction) {
        for (double d = beamSpacing; d <= range; d += beamSpacing) {
            if (d < beamVisualStartOffset) continue;
            drawVisual.play(eye.clone().add(direction.clone().multiply(d)));
        }
    }

    private void sweepDrawBeam(Hero hero, Location eye, Vector direction) {
        LivingEntity self = hero.getController().getEntity();
        Hitbox.cylinder(eye, direction, beamRadius, range)
                .exclude(self)
                .excludeTeammates(self)
                .onHit(victim -> {
                    long now = System.currentTimeMillis();
                    Long last = drawHitTimestamps.get(victim.getUniqueId());
                    if (last != null && now - last < drawHitIntervalMs) return;
                    drawHitTimestamps.put(victim.getUniqueId(), now);
                    hero.triggerAbilityHit(this, victim);
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(drawDamage)
                            .execute();
                })
                .check();
    }

    private void sendDrawBar(Hero hero) {
        double fraction = Math.min(1.0, drawTicks / (double) drawTimeTicks);
        int bars = (int) Math.round(fraction * 10);
        String fill = "█".repeat(bars) + "░".repeat(10 - bars);
        hero.getController().sendActionBar(Component.text("Skyfall ", NamedTextColor.AQUA)
                .append(Component.text(fill, fraction >= 1.0 ? NamedTextColor.RED : NamedTextColor.YELLOW)));
    }

    private void fireBurst(Hero hero, Location eye, Vector direction) {
        LivingEntity self = hero.getController().getEntity();
        burstSound.play(eye, hero.getController());
        for (double d = beamSpacing; d <= range; d += beamSpacing) {
            if (d < beamVisualStartOffset) continue;
            burstVisual.play(eye.clone().add(direction.clone().multiply(d)));
        }
        Hitbox.cylinder(eye, direction, burstRadius, range)
                .exclude(self)
                .excludeTeammates(self)
                .onHit(victim -> {
                    hero.triggerAbilityHit(this, victim);
                    Interactions.on(self, victim)
                            .abilityName(getName())
                            .damage(burstDamage)
                            .knockback(burstKnockback)
                            .direction(direction)
                            .execute();
                })
                .check();

        drawTicks = -1;
        drawHitTimestamps.clear();
        spendCharge(hero);
    }

    private void forfeitCharge(Hero hero) {
        chargeLostSound.play(hero.getController().getLocation(), hero.getController());
        hero.getController().sendActionBar(Component.text("Charge lost!", NamedTextColor.RED));
        spendCharge(hero);
    }

    private void spendCharge(Hero hero) {
        shotsLeft--;
        windowTicksLeft = windowTicks;
        if (shotsLeft <= 0) {
            endChannel(hero);
        }
    }

    private void endChannel(Hero hero) {
        if (!channeling) return;
        channeling = false;
        drawTicks = -1;
        drawHitTimestamps.clear();
        hero.getAttributes().setDamageMultiplier(storedDamageTakenMultiplier);
        hero.getAttributes().setKnockbackMultiplier(storedKnockbackTakenMultiplier);
    }

    @Override
    public String getActiveStatusText() {
        if (!channeling) return null;
        return shotsLeft + (shotsLeft == 1 ? " shot left" : " shots left");
    }

    @Override
    public void onDeath(Hero hero) {
        super.onDeath(hero);
        endChannel(hero);
    }

    @Override
    public void onOwnerLost(Hero hero) {
        super.onOwnerLost(hero);
        endChannel(hero);
    }
}
