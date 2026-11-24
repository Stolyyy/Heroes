package me.stolyy.heroes.ability.kit.blud;

import me.stolyy.heroes.ability.AbilityType;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.ability.template.CompositeAbility;
import me.stolyy.heroes.ability.template.Dash;
import me.stolyy.heroes.ability.template.Hitscan;
import me.stolyy.heroes.ability.template.Projectile;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.util.Configs;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;

public class BludPrimary extends CompositeAbility {
    public enum Mode { DASH, BOOMERANG, TRACKING }

    private final Dash dashMode;
    private final Projectile boomerangMode;
    private final Hitscan trackingMode;

    private Mode mode = Mode.DASH;

    public BludPrimary(ConfigurationSection config, HeroSkin skin) {
        super(config, skin);
        this.dashMode = addChild(new Dash(Configs.requireSection(this.config, "dash"), skin, AbilityType.PRIMARY) {});
        this.boomerangMode = addChild(new Projectile(Configs.requireSection(this.config, "boomerang"), skin, AbilityType.PRIMARY));
        this.trackingMode = addChild(new Hitscan(Configs.requireSection(this.config, "tracking"), skin, AbilityType.PRIMARY));
    }

    private BaseAbility activeMode() {
        return switch (mode) {
            case DASH -> dashMode;
            case BOOMERANG -> boomerangMode;
            case TRACKING -> trackingMode;
        };
    }

    @Override
    protected boolean canUse(Hero hero) {
        return activeMode().canExecute(hero);
    }

    @Override
    public void perform(Hero hero) {
        activeMode().execute(hero);
    }

    @Override
    public Cooldown getDisplayCooldown(Hero hero) {
        return activeMode().getCooldownObject();
    }

    void advanceMode(Hero hero) {
        mode = Mode.values()[(mode.ordinal() + 1) % Mode.values().length];
        announce(hero);
    }

    void retreatMode(Hero hero) {
        mode = Mode.values()[(mode.ordinal() + Mode.values().length - 1) % Mode.values().length];
        announce(hero);
    }

    private void announce(Hero hero) {
        hero.getController().sendActionBar(Component.text("Mode: " + mode.name(), NamedTextColor.RED));
    }
}
