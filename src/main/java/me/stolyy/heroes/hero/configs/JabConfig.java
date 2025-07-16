package me.stolyy.heroes.hero.configs;

public record JabConfig(
        double damage,
        double knockback,
        double cooldown,
        double reach,
        String hitSound,
        String swingSound
) { }
