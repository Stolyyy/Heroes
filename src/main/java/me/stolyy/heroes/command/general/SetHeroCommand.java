package me.stolyy.heroes.command.general;

import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SetHeroCommand extends Command {

    private static final Map<String, String> HERO_ALIASES = Map.ofEntries(
            Map.entry("void", "void_crawler"),
            Map.entry("voidcrawler", "void_crawler"),
            Map.entry("skull", "skullfire"),
            Map.entry("spiderman", "spooderman"),
            Map.entry("spidey", "spooderman"),
            Map.entry("spider", "spooderman")
    );

    public SetHeroCommand() {
        super("setCharacter");
        setDescription("Select your character");
        setUsage("/setCharacter [characterName]");
        this.setAliases(List.of("hero", "sethero"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // No in-game gate: selection is next-game-only (Game.start builds the live hero once from
        // the stored pick), so players and spectators alike may re-pick mid-match. The title
        // carries that context; HeroManager sends the matching chat line and leaves the match
        // sidebar alone.
        if (args.length == 0) {
            GUIManager.open(player, new HeroSelectGUI(player,
                    GameManager.isPlayerInGame(player) ? "Hero for Next Game" : "Select Your Hero"));
            return true;
        }

        // Resolve alias to actual hero name
        String heroName = resolveHeroName(args[0]);
        if (!HeroConfig.exists(heroName)) {
            player.sendMessage(Component.text("Hero not found: " + args[0], NamedTextColor.RED));
            return true;
        }

        // Admin-only check
        if (HeroConfig.isAdminOnly(heroName) && !player.isOp()) {
            player.sendMessage(Component.text("Hero not found: " + args[0], NamedTextColor.RED));
            return true;
        }

        // Target player argument for ops: /sethero <hero> <player>
        if (args.length >= 2 && player.isOp()) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return true;
            }
            HeroManager.selectHero(target, heroName);
            player.sendMessage(Component.text("Set " + target.getName() + "'s hero to "
                    + HeroConfig.formatName(heroName)
                    + (GameManager.isPlayerInGame(target) ? " (takes effect next game)." : "."),
                    NamedTextColor.GREEN));
            return true;
        }

        HeroManager.selectHero(player, heroName);
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
        return true;
    }

    private String resolveHeroName(String input) {
        String lowercaseInput = input.toLowerCase();
        // Check aliases first
        if (HERO_ALIASES.containsKey(lowercaseInput)) {
            return HERO_ALIASES.get(lowercaseInput);
        }
        // Return as-is (HeroManager will validate)
        return lowercaseInput;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        boolean isOp = (sender instanceof Player p) && p.isOp();
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String name : HeroConfig.getAvailableNames()) {
                if (!HeroConfig.isAdminOnly(name) || isOp) {
                    options.add(name);
                }
            }
            // Add aliases, filtering admin-only resolved targets for non-ops
            for (String alias2 : HERO_ALIASES.keySet()) {
                String resolved = HERO_ALIASES.get(alias2);
                if (!HeroConfig.isAdminOnly(resolved) || isOp) {
                    options.add(alias2);
                }
            }
            return options.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .distinct()
                    .sorted()
                    .toList();
        }
        if (args.length == 2 && isOp) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .toList();
        }
        return Collections.emptyList();
    }
}
