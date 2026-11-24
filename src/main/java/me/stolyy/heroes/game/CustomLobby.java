package me.stolyy.heroes.game;

import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.map.MapPool;
import me.stolyy.heroes.game.queue.QueueManager;
import me.stolyy.heroes.game.rules.GameModePreset;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import me.stolyy.heroes.vfx.ui.gui.GUIManager;
import me.stolyy.heroes.vfx.ui.gui.GameModeGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Custom match lobby assembly: party gating, map selection, team seeding, and setup GUI opening. */
public final class CustomLobby {

    private CustomLobby() {}

    public static void open(Player player, @Nullable String mapName) {
        open(player, mapName, null);
    }

    public static void open(Player player, @Nullable String mapName, @Nullable GameModePreset preset) {
        Party party = PartyManager.getPlayerParty(player);
        if (party != null && party.getSize() > GameMode.CUSTOM.maxTotalPlayers()) {
            player.sendMessage(Component.text(
                    "Your party is too large for this mode (max " + GameMode.CUSTOM.maxTotalPlayers() + " players).",
                    NamedTextColor.RED));
            return;
        }

        for (Player member : PartyManager.getPartyMembers(player)) {
            if (GameManager.isPlayerInGame(member)) {
                player.sendMessage(Component.text(member.getName() + " is currently in a game.", NamedTextColor.RED));
                return;
            }
        }

        GameMap map;
        if (mapName != null) {
            map = GameMapManager.findMapByName(mapName);
            if (map == null) {
                player.sendMessage(Component.text("Map not found: " + mapName, NamedTextColor.RED));
                return;
            }
        } else {
            map = GameMapManager.getRandomMapForPool(MapPool.BOTH);
        }

        Game game = Game.create(map, GameMode.CUSTOM);

        List<Player> members = new ArrayList<>(PartyManager.getPartyMembers(player));
        if (members.isEmpty()) members.add(player);
        members.forEach(QueueManager::removeFromAllQueues);

        members.remove(player);
        game.addPlayer(player, TeamColor.RED);
        if (!members.isEmpty()) game.addPlayer(members.getFirst(), TeamColor.BLUE);
        for (int i = 1; i < members.size(); i++) {
            game.addPlayer(members.get(i), TeamColor.SPECTATOR);
        }

        CustomGameGUI partyGUI = new CustomGameGUI(game, player);
        if (preset != null) {
            game.setGameModePreset(preset);
            GUIManager.open(player, GameModeGUI.settingsGuiFor(preset, game, player, partyGUI));
        } else {
            GUIManager.open(player, partyGUI);
        }
    }
}
