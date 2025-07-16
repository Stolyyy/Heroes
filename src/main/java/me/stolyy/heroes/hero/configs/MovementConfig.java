package me.stolyy.heroes.hero.configs;

public record MovementConfig(
        int maxDoubleJumps,
        double walkSpeed,
        double jumpHeight,
        double gravity
) { }
