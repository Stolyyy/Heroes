package me.stolyy.heroes.heros.controllers;

import org.bukkit.entity.Entity;

public class BotController implements Controller {
    private final Entity owner;

    public BotController(Entity owner) {
        this.owner = owner;
    }

    @Override
    public Entity getOwner() {
        return null;
    }
}
