package me.stolyy.heroes.hero.characters;

import me.stolyy.heroes.hero.abilities.Ability;
import me.stolyy.heroes.hero.components.*;
import me.stolyy.heroes.hero.data.HeroData;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class Hero {
    protected final Player player;
    protected final HeroData data;
    protected final Map<String, Ability> abilities;

    protected double damageMultiplier = 1.0;
    protected double knockbackMultiplier = 1.0;

    protected Equipment equipment;
    protected Movement movement;
    protected Jab jab;
    protected Display display;
    protected Energy energy;

    public Hero(Player player) {
        this.player = player;
        this.data = DataManager.getHeroData(this.getClass().getSimpleName());
        this.abilities = new HashMap<>();
        setBaseHealth(data.getMaxHealth());



        defineAbilities();

        equipment.equip();
    }

    protected abstract void defineAbilities();
    public void usePrimary(){
        if (abilities.containsKey("primary")) abilities.get("primary").execute();
    }
    public void useSecondary(){
        if (abilities.containsKey("secondary")) abilities.get("secondary").execute();
    }
    public void useUltimate(){
        if (abilities.containsKey("ultimate")) abilities.get("ultimate").execute();
    }

    public void jab(Player target) {
        getComponent(Jab.class).jab(target);
        onMeleeHit();
    }
    public void onToggleFlight() {
        Movement movement = getComponent(Movement.class);
        if(movement.canDoubleJump()) movement.performDoubleJump();
    }
    public void onLanding() {
        getComponent(Movement.class).resetDoubleJumps();
    }

    protected void onMeleeHit() {}
    protected void onRangedHit(){}

    protected void onTick() {
        for (HeroComponent component : components.values()) component.onTick();
    }

    public void onStart() {}
    public void onDeath() {}
    public void onRespawn() {}
    public void onElimination() { clean();}

    public void clean() {
        for (HeroComponent component : components.values()) component.clean();
    }

    private void setBaseHealth(double health) {
        Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).setBaseValue(health);
        player.setHealth(health);
        player.setHealthScale(20.0);
        player.setHealthScaled(true);
    }

    public final Player getPlayer() { return player; }
}
