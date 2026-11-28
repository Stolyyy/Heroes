package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.progression.ShardKind;
import me.stolyy.heroes.progression.SkinShards;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.cosmetic.HeroSkin.Rarity;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.ShardsGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * {@code /shards} — opens {@link ShardsGUI}, plus admin subcommands behind
 * {@value #ADMIN_PERMISSION}: {@code /shards give <player> <random|hero|skin> <normal|rare|legendary> [amount]}
 * and {@code /shards check <player>}.
 */
public class ShardsCommand extends HeroesCommand {
    public static final String ADMIN_PERMISSION = "heroes.shards.admin";

    public ShardsCommand() {
        super("shards", "Open Skin Shards menu or manage player shards", "/shards",
                List.of("shard"), null, false);
    }

    @Override
    public List<String> extraPermissions() {
        return List.of(ADMIN_PERMISSION);
    }

    @Override
    protected void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
                return;
            }
            GUIManager.open(player, new ShardsGUI(player, null));
            return;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("give") || sub.equals("check")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                return;
            }
            if (sub.equals("check")) executeCheck(sender, args);
            else executeGive(sender, args);
            return;
        }

        sender.sendMessage(GameConfigManager.getMessage(
                sender.hasPermission(ADMIN_PERMISSION) ? "shards.admin-usage" : "shards.usage"));
    }

    private void executeCheck(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(GameConfigManager.getMessage("shards.admin-usage"));
            return;
        }
        ResolvedPlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(GameConfigManager.getMessage("shards.player-not-found", Map.of("player", args[1])));
            return;
        }

        PlayerStats stats = target.stats();
        sender.sendMessage(GameConfigManager.getMessage("shards.check-header", Map.of(
                "player", target.name(),
                "pity", String.valueOf(stats.getShardPity()))));
        for (ShardKind kind : ShardKind.values()) {
            sender.sendMessage(GameConfigManager.getMessage("shards.check-line", Map.of(
                    "kind", kind.displayName(),
                    "normal", String.valueOf(stats.getShardCount(kind, Rarity.NORMAL)),
                    "rare", String.valueOf(stats.getShardCount(kind, Rarity.RARE)),
                    "legendary", String.valueOf(stats.getShardCount(kind, Rarity.LEGENDARY)))));
        }
    }

    private void executeGive(CommandSender sender, String[] args) {
        if (args.length != 4 && args.length != 5) {
            sender.sendMessage(GameConfigManager.getMessage("shards.admin-usage"));
            return;
        }
        ResolvedPlayer target = resolvePlayer(args[1]);
        if (target == null) {
            sender.sendMessage(GameConfigManager.getMessage("shards.player-not-found", Map.of("player", args[1])));
            return;
        }

        ShardKind kind = ShardKind.fromCommandToken(args[2]);
        if (kind == null) {
            sender.sendMessage(GameConfigManager.getMessage("shards.unknown-kind", Map.of("kind", args[2])));
            return;
        }

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(args[3].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(GameConfigManager.getMessage("shards.unknown-rarity", Map.of("rarity", args[3])));
            return;
        }

        int amount = 1;
        if (args.length == 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(GameConfigManager.getMessage("shards.invalid-amount", Map.of("amount", args[4])));
                return;
            }
            if (amount <= 0) {
                sender.sendMessage(GameConfigManager.getMessage("shards.invalid-amount", Map.of("amount", args[4])));
                return;
            }
        }

        PlayerStats stats = target.stats();
        stats.addShards(kind, rarity, amount);
        PlayerStatsManager.saveStats(stats);

        Map<String, String> vars = Map.of(
                "player", target.name(),
                "amount", String.valueOf(amount),
                "kind", kind.displayName(),
                "rarity", SkinShards.formatRarity(rarity));
        sender.sendMessage(GameConfigManager.getMessage("shards.given", vars));
        if (target.online() != null && target.online() != sender) {
            target.online().sendMessage(GameConfigManager.getMessage("shards.given-notify", vars));
        }
    }

    private record ResolvedPlayer(Player online, PlayerStats stats, String name) {}

    private static ResolvedPlayer resolvePlayer(String arg) {
        Player online = Bukkit.getPlayerExact(arg);
        if (online == null) online = Bukkit.getPlayer(arg);
        if (online != null) {
            return new ResolvedPlayer(online, PlayerStatsManager.getStats(online), online.getName());
        }
        PlayerStats offline = PlayerStatsManager.lookupByName(arg);
        if (offline == null) return null;
        String name = offline.lastKnownName() != null ? offline.lastKnownName() : arg;
        return new ResolvedPlayer(null, offline, name);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) return Collections.emptyList();

        if (args.length == 1) {
            return TabComplete.startsWith(List.of("give", "check"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("check")) {
            return args.length == 2 ? TabComplete.onlinePlayers(args[1]) : Collections.emptyList();
        }
        if (sub.equals("give")) {
            return switch (args.length) {
                case 2 -> TabComplete.onlinePlayers(args[1]);
                case 3 -> TabComplete.startsWith(List.of("random", "hero", "skin"), args[2]);
                case 4 -> TabComplete.startsWith(List.of("normal", "rare", "legendary"), args[3]);
                case 5 -> TabComplete.startsWith(List.of("1", "3", "5"), args[4]);
                default -> Collections.emptyList();
            };
        }
        return Collections.emptyList();
    }
}
