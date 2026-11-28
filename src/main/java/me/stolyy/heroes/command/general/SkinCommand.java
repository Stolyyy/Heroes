package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.SkinSelectGUI;
import me.stolyy.heroes.vfx.ui.visual.HeroNpcSkins;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /skin [hero] [skin]} (alias {@code /cosmetic}) — opens the skin menu or equips an owned
 * skin directly, plus admin subcommands {@code /skin grant|revoke <player> <hero> <skin>} behind
 * {@value #ADMIN_PERMISSION}.
 */
public class SkinCommand extends HeroesCommand {
    public static final String ADMIN_PERMISSION = "heroes.skin.admin";

    public SkinCommand() {
        super("skin", "Select or manage hero skins", "/skin [hero] [skin]",
                List.of("cosmetic"), null, false);
    }

    @Override
    public List<String> extraPermissions() {
        return List.of(ADMIN_PERMISSION);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length >= 1 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("revoke"))) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            executeAdmin(sender, args);
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return;
        }

        if (args.length == 0) {
            String heroName = HeroManager.getHeroName(player);
            if (heroName == null || !HeroConfig.exists(heroName)) {
                player.sendMessage(GameConfigManager.getMessage("skin.no-hero-selected"));
                return;
            }
            GUIManager.open(player, new SkinSelectGUI(player, heroName, null));
            return;
        }

        String heroName = HeroConfig.resolve(args[0]);
        if (!HeroConfig.exists(heroName) || heroName.equals("default")
                || (HeroConfig.isAdminOnly(heroName) && !player.isOp())) {
            player.sendMessage(GameConfigManager.getMessage("skin.unknown-hero", Map.of("hero", args[0])));
            return;
        }

        if (args.length == 1) {
            GUIManager.open(player, new SkinSelectGUI(player, heroName, null));
            return;
        }

        if (args.length == 2) {
            HeroSkin skin = CosmeticManager.findSkin(heroName, args[1]);
            if (skin == null || skin.isHidden()) {
                player.sendMessage(GameConfigManager.getMessage("skin.unknown-skin", Map.of(
                        "hero", HeroConfig.formatName(heroName),
                        "skin", args[1])));
                return;
            }

            PlayerStats stats = PlayerStatsManager.getStats(player);
            if (!stats.ownsSkin(heroName, skin.getId())) {
                SoundBuilder.of(Sound.ENTITY_VILLAGER_NO).play(player);
                player.sendMessage(GameConfigManager.getMessage("skin.locked", Map.of(
                        "hero", HeroConfig.formatName(heroName),
                        "skin", skin.getDisplayName())));
                return;
            }

            SkinSelectGUI.applySelection(player, heroName, skin);
            return;
        }

        player.sendMessage(GameConfigManager.getMessage("skin.usage"));
    }

    private void executeAdmin(CommandSender sender, String[] args) {
        if (args.length != 4) {
            sender.sendMessage(GameConfigManager.getMessage("skin.admin-usage"));
            return;
        }

        boolean grant = args[0].equalsIgnoreCase("grant");
        String playerArg = args[1];
        Player onlineTarget = Bukkit.getPlayerExact(playerArg);
        if (onlineTarget == null) onlineTarget = Bukkit.getPlayer(playerArg);

        PlayerStats stats = onlineTarget != null
                ? PlayerStatsManager.getStats(onlineTarget)
                : PlayerStatsManager.lookupByName(playerArg);
        if (stats == null) {
            sender.sendMessage(GameConfigManager.getMessage("skin.player-not-found", Map.of("player", playerArg)));
            return;
        }
        String targetName = onlineTarget != null
                ? onlineTarget.getName()
                : (stats.lastKnownName() != null ? stats.lastKnownName() : playerArg);

        String heroName = HeroConfig.resolve(args[2]);
        if (!HeroConfig.exists(heroName) || heroName.equals("default")) {
            sender.sendMessage(GameConfigManager.getMessage("skin.unknown-hero", Map.of("hero", args[2])));
            return;
        }

        HeroSkin skin = CosmeticManager.findSkin(heroName, args[3]);
        if (skin == null || skin.isHidden()) {
            sender.sendMessage(GameConfigManager.getMessage("skin.unknown-skin", Map.of(
                    "hero", HeroConfig.formatName(heroName),
                    "skin", args[3])));
            return;
        }

        if (skin.getId().equalsIgnoreCase("default")) {
            sender.sendMessage(GameConfigManager.getMessage("skin.default-permanent"));
            return;
        }

        Map<String, String> vars = Map.of(
                "player", targetName,
                "hero", HeroConfig.formatName(heroName),
                "skin", skin.getDisplayName());

        if (grant) {
            if (!stats.grantSkin(heroName, skin.getId())) {
                sender.sendMessage(GameConfigManager.getMessage("skin.already-owned", vars));
                return;
            }
            PlayerStatsManager.saveStats(stats);
            sender.sendMessage(GameConfigManager.getMessage("skin.granted", vars));
            if (onlineTarget != null && onlineTarget != sender) {
                onlineTarget.sendMessage(GameConfigManager.getMessage("skin.granted-notify", vars));
            }
        } else {
            if (!stats.revokeSkin(heroName, skin.getId())) {
                sender.sendMessage(GameConfigManager.getMessage("skin.not-owned", vars));
                return;
            }
            PlayerStatsManager.saveStats(stats);
            if (onlineTarget != null) {
                HeroNpcSkins.refresh(onlineTarget, heroName);
            }
            sender.sendMessage(GameConfigManager.getMessage("skin.revoked", vars));
            if (onlineTarget != null && onlineTarget != sender) {
                onlineTarget.sendMessage(GameConfigManager.getMessage("skin.revoked-notify", vars));
            }
        }
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        boolean isOp = !(sender instanceof Player p) || p.isOp();
        boolean isAdmin = sender.hasPermission(ADMIN_PERMISSION);

        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            for (String name : HeroConfig.getAvailableNames()) {
                if (!HeroConfig.isAdminOnly(name) || isOp) {
                    options.add(name);
                }
            }
            for (Map.Entry<String, String> entry : HeroConfig.aliases().entrySet()) {
                if (!HeroConfig.isAdminOnly(entry.getValue()) || isOp) {
                    options.add(entry.getKey());
                }
            }
            if (isAdmin) {
                options.add("grant");
                options.add("revoke");
            }
            return TabComplete.startsWith(options, args[0]);
        }

        String first = args[0].toLowerCase(Locale.ROOT);
        if (isAdmin && (first.equals("grant") || first.equals("revoke"))) {
            if (args.length == 2) {
                return TabComplete.onlinePlayers(args[1]);
            }
            if (args.length == 3) {
                return TabComplete.startsWith(HeroConfig.getAvailableNames(), args[2]);
            }
            if (args.length == 4) {
                String heroName = HeroConfig.resolve(args[2]);
                if (!HeroConfig.exists(heroName)) return Collections.emptyList();
                List<String> skins = CosmeticManager.selectableSkins(heroName).stream()
                        .map(HeroSkin::getId)
                        .filter(id -> !id.equalsIgnoreCase("default"))
                        .toList();
                return TabComplete.startsWith(skins, args[3]);
            }
            return Collections.emptyList();
        }

        if (args.length == 2) {
            String heroName = HeroConfig.resolve(args[0]);
            if (!HeroConfig.exists(heroName) || (HeroConfig.isAdminOnly(heroName) && !isOp)) {
                return Collections.emptyList();
            }
            List<String> skins = CosmeticManager.selectableSkins(heroName).stream()
                    .map(HeroSkin::getId)
                    .toList();
            return TabComplete.startsWith(skins, args[1]);
        }

        return Collections.emptyList();
    }
}
