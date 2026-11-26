package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class ForceReadyCommand extends HeroesCommand {

    public ForceReadyCommand() {
        super("forceready", "Admin command to force-ready a hero ability cooldown",
                "/forceready <primary|secondary|smash|all>",
                List.of("fr", "ready"), "heroes.forceready", true);
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return;
        }

        Hero hero = HeroManager.getHero(player);
        if (hero == null) {
            player.sendMessage(Component.text("You do not have an active hero.", NamedTextColor.RED));
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "primary" -> {
                forceReady(hero.getPrimaryAbility());
                player.sendMessage(Component.text("Primary ability forced ready.", NamedTextColor.GREEN));
            }
            case "secondary" -> {
                forceReady(hero.getSecondaryAbility());
                player.sendMessage(Component.text("Secondary ability forced ready.", NamedTextColor.GREEN));
            }
            case "smash" -> {
                forceReady(hero.getSmashAbility());
                player.sendMessage(Component.text("Smash ability forced ready.", NamedTextColor.GREEN));
            }
            case "all" -> {
                forceReady(hero.getPrimaryAbility());
                forceReady(hero.getSecondaryAbility());
                forceReady(hero.getSmashAbility());
                player.sendMessage(Component.text("All abilities forced ready.", NamedTextColor.GREEN));
            }
            default -> player.sendMessage(Component.text(
                    "Unknown ability type: " + args[0] + ". Use primary, secondary, smash, or all.", NamedTextColor.RED));
        }
    }

    private void forceReady(Ability ability) {
        if (ability != null) {
            // getAllCooldowns, not getCooldownObject: a variant family (Bug's directional
            // secondary) owns a timer per variant, and clearing only the shared one leaves the
            // dive locked — which is exactly when you'd reach for this command.
            ability.getAllCooldowns().forEach(Cooldown::forceReady);
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return TabComplete.startsWith(List.of("primary", "secondary", "smash", "all"), args[0]);
        }
        return List.of();
    }
}
