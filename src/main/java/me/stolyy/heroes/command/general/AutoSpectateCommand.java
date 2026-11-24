package me.stolyy.heroes.command.general;

import me.stolyy.heroes.command.HeroesCommand;
import me.stolyy.heroes.command.TabComplete;
import me.stolyy.heroes.game.AutoSpectate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * {@code /autospec <player>} — persistent auto-spectate (see {@link AutoSpectate}).
 * {@code /autospec off} disarms; no args shows the current arm.
 */
public class AutoSpectateCommand extends HeroesCommand {

    public AutoSpectateCommand() {
        super("autospectate", "Automatically spectate a player whenever their game starts",
                "/autospec <player|off>", List.of("autospec", "as", "aspec"));
    }

    @Override
    protected void execute(Player player, String[] args) {
        if (args.length == 0) {
            UUID targetId = AutoSpectate.target(player.getUniqueId());
            if (targetId == null) {
                player.sendMessage(Component.text("Auto-spectate is off. Usage: " + getUsage(), NamedTextColor.YELLOW));
            } else {
                Player target = Bukkit.getPlayer(targetId);
                String name = target != null ? target.getName() : "an offline player";
                player.sendMessage(Component.text("Auto-spectating " + name + " — /autospec off to stop.", NamedTextColor.AQUA));
            }
            return;
        }

        if (args[0].equalsIgnoreCase("off")) {
            if (AutoSpectate.disarm(player.getUniqueId())) {
                player.sendMessage(Component.text("Auto-spectate disarmed.", NamedTextColor.YELLOW));
            } else {
                player.sendMessage(Component.text("Auto-spectate was not armed.", NamedTextColor.GRAY));
            }
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("You cannot auto-spectate yourself.", NamedTextColor.RED));
            return;
        }

        AutoSpectate.arm(player, target);
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("off");
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !(sender instanceof Player self) || !p.getUniqueId().equals(self.getUniqueId()))
                    .map(Player::getName)
                    .forEach(options::add);
            return TabComplete.startsWith(options, args[0]);
        }
        return Collections.emptyList();
    }
}
