package me.stolyy.heroes.hero;

import me.stolyy.heroes.vfx.effect.SoundEffect;
import me.stolyy.heroes.vfx.effect.VisualEffect;

public record HeroStats(
    AttributeStats attributes,
    JumpStats mobility,
    CombatStats combat,
    EnergyStats energy
) {
    public record AttributeStats(
        HeroType type,
        double weight,
        double maxHealth,
        double movementSpeed,
        double jumpHeight,
        double gravity,
        double stepHeight,
        double scale
    ) {}

    public record JumpStats(
        int maxDoubleJumps,
        double xStrength,
        double yStrength,
        SoundEffect jumpSound,
        VisualEffect jumpEffect
    ) {}

    public record CombatStats(
        double jabRange,
        double jabRadius,
        boolean jabPierce,
        double jabCooldown,
        double jabDamage,
        double jabKnockback,
        SoundEffect swingSound,
        SoundEffect hitSound
    ) {}

    public record EnergyStats(
        int startingEnergy,
        int maxEnergy,
        double energyPerTick,
        boolean canIncreaseEnergy
    ) {}
}
