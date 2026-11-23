package me.stolyy.heroes.command.general;

import me.stolyy.heroes.game.AutoSpectate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@code /autospec <player>} — persistent auto-spectate (see {@link AutoSpectate}).
 * {@code /autospec off} disarms; no args shows the current arm.
 */
public class AutoSpectateCommand extends Command {

    public AutoSpectateCommand() {
        super("autospectate");
        this.setDescription("Automatically spectate a player whenever their game starts");
        this.setUsage("/autospec <player|off>");
        this.setAliases(List.of("autospec", "as", "aspec"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            UUID targetId = AutoSpectate.target(player.getUniqueId());
            if (targetId == null) {
                player.sendMessage(Component.text("Auto-spectate is off. Usage: " + getUsage(), NamedTextColor.YELLOW));
            } else {
                Player target = Bukkit.getPlayer(targetId);
                String name = target != null ? target.getName() : "an offline player";
                player.sendMessage(Component.text("Auto-spectating " + name + " — /autospec off to stop.", NamedTextColor.AQUA));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            if (AutoSpectate.disarm(player.getUniqueId())) {
                player.sendMessage(Component.text("Auto-spectate disarmed.", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Auto-spectate was not armed.", NamedTextColor.GRAY));
            }
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot auto-spectate yourself.", NamedTextColor.RED));
            return true;
        }

        AutoSpectate.arm(player, target);
        return true;
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("off");
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !(sender instanceof Player self) || !p.getUniqueId().equals(self.getUniqueId()))
                    .map(Player::getName)
                    .forEach(options::add);
            return options.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .toList();
        }
        return Collections.emptyList();
    }
}
