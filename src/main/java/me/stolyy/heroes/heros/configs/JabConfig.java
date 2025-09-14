package me.stolyy.heroes.heros.configs;

public record JabConfig(
        double damage,
        double knockback,
        double cooldown,
        double reach,
        String hitSound,
        String swingSound
) { }
