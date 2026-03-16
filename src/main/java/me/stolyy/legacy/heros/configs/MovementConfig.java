package me.stolyy.heroes.heros.configs;

public record MovementConfig(
        int maxDoubleJumps,
        double doubleJumpMultiplier, // 0.9
        double doubleJumpYValue, //0.85
        double walkSpeed,
        double jumpHeight,
        double gravity
) { }
