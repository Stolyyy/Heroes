package me.stolyy.heroes.control.ai;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.control.BotController;
import org.bukkit.entity.LivingEntity;

public interface BotAI {
    void onSpawn(Hero hero, BotController controller);
    void tick(Hero hero, BotController controller);
    void onDamageTaken(Hero hero, double damage, LivingEntity source);
    void cleanup();

    LivingEntity getTarget();
    void setTarget(LivingEntity target);
}
