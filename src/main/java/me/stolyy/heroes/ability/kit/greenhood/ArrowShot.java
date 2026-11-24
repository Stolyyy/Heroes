package me.stolyy.heroes.ability.kit.greenhood;

import me.stolyy.heroes.hero.Hero;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;

interface ArrowShot {
    void onEntityHit(Hero hero, LivingEntity victim, AbstractArrow arrow);
    void onGroundHit(Hero hero, AbstractArrow arrow);
}
