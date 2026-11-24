package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.HeroSelectGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SetHeroCommand extends HeroesCommand {

    public SetHeroCommand() {
        super("setcharacter", "Select your character",
                "/setCharacter [characterName]", List.of("hero", "sethero"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        // No in-game gate: selection is next-game-only (Game.start builds the live hero once from
        // the stored pick), so players and spectators alike may re-pick mid-match. The title
        // carries that context; HeroManager sends the matching chat line and leaves the match
        // sidebar alone.
        if (args.length == 0) {
            GUIManager.open(player, GameManager.isPlayerInGame(player)
                    ? HeroSelectGUI.lobbyNextGame(player)
                    : HeroSelectGUI.lobby(player));
            return;
        }

        String heroName = HeroConfig.resolve(args[0]);
        if (!HeroConfig.exists(heroName)) {
            player.sendMessage(Component.text("Hero not found: " + args[0], NamedTextColor.RED));
            return;
        }

        if (HeroConfig.isAdminOnly(heroName) && !player.isOp()) {
            player.sendMessage(Component.text("Hero not found: " + args[0], NamedTextColor.RED));
            return;
        }

        if (args.length >= 2 && player.isOp()) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
                return;
            }
            HeroManager.selectHero(target, heroName);
            player.sendMessage(Component.text("Set " + target.getName() + "'s hero to "
                    + HeroConfig.formatName(heroName)
                    + (GameManager.isPlayerInGame(target) ? " (takes effect next game)." : "."),
                    NamedTextColor.GREEN));
            return;
        }

        HeroManager.selectHero(player, heroName);
        SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP).pitch(1.0f).play(player);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        boolean isOp = (sender instanceof Player p) && p.isOp();
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String name : HeroConfig.getAvailableNames()) {
                if (!HeroConfig.isAdminOnly(name) || isOp) {
                    options.add(name);
                }
            }
            for (Map.Entry<String, String> entry : HeroConfig.aliases().entrySet()) {
                String key = entry.getKey();
                if (!HeroConfig.isAdminOnly(entry.getValue()) || isOp) {
                    options.add(key);
                }
            }
            return TabComplete.startsWith(options, args[0]);
        }
        if (args.length == 2 && isOp) {
            return TabComplete.onlinePlayers(args[1]);
        }
        return Collections.emptyList();
    }
}
