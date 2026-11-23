package me.stolyy.heroes.command.admin;

import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.ability.Ability;
import me.stolyy.heroes.ability.Cooldown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ForceReadyCommand extends Command {

    public ForceReadyCommand() {
        super("forceready");
        this.setDescription("Admin command to force-ready a hero ability cooldown");
        this.setUsage("/forceready <primary|secondary|smash|all>");
        this.setPermission("heroes.forceready");
        this.setAliases(List.of("fr", "ready"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission(getPermission())) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: " + getUsage(), NamedTextColor.YELLOW));
            return true;
        }

        Hero hero = HeroManager.getHero(player);
        if (hero == null) {
            player.sendMessage(Component.text("You do not have an active hero.", NamedTextColor.RED));
            return true;
        }

        String arg = args[0].toLowerCase();
        switch (arg) {
            case "primary" -> {
                forceReady(hero.getPrimaryAbility(), "Primary");
                player.sendMessage(Component.text("Primary ability forced ready.", NamedTextColor.GREEN));
            }
            case "secondary" -> {
                forceReady(hero.getSecondaryAbility(), "Secondary");
                player.sendMessage(Component.text("Secondary ability forced ready.", NamedTextColor.GREEN));
            }
            case "smash" -> {
                forceReady(hero.getSmashAbility(), "Smash");
                player.sendMessage(Component.text("Smash ability forced ready.", NamedTextColor.GREEN));
            }
            case "all" -> {
                forceReady(hero.getPrimaryAbility(), "Primary");
                forceReady(hero.getSecondaryAbility(), "Secondary");
                forceReady(hero.getSmashAbility(), "Smash");
                player.sendMessage(Component.text("All abilities forced ready.", NamedTextColor.GREEN));
            }
            default -> {
                player.sendMessage(Component.text("Unknown ability type: " + args[0] + ". Use primary, secondary, smash, or all.", NamedTextColor.RED));
            }
        }

        return true;
    }

    private void forceReady(Ability ability, String label) {
        if (ability != null) {
            // getAllCooldowns, not getCooldownObject: a variant family (Bug's directional
            // secondary) owns a timer per variant, and clearing only the shared one leaves the
            // dive locked — which is exactly when you'd reach for this command.
            ability.getAllCooldowns().forEach(Cooldown::forceReady);
        }
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("primary", "secondary", "smash", "all").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
