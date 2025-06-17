package me.stolyy.heroes.game.minigame;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.maps.GameMap;
import me.stolyy.heroes.game.maps.GameMapManager;
import me.stolyy.heroes.game.menus.GUIManager;
import me.stolyy.heroes.game.menus.PartyModeGUI;
import me.stolyy.heroes.party.Party;
import me.stolyy.heroes.party.PartyManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.*;

public class GameManager {
    private static final Set<Game> waitingGames = new LinkedHashSet<>();
    private static final Map<UUID, Game> playerGames = new HashMap<>();


    public static void joinGame(Player player, GameEnums.GameMode gameMode) {
        if (playerGames.containsKey(player.getUniqueId())) {
            leaveGame(player, false);
        }

        switch (gameMode) {
            case ONE_V_ONE -> handle1v1Join(player);
            case TWO_V_TWO -> handle2v2Join(player);
            case PARTY -> handlePartyJoin(player);
        }
    }

    public static void spectateGame(Player spectator, Player target) {
        Game game = getPlayerGame(target);
        if (game == null || game.gameState() != GameEnums.GameState.IN_PROGRESS) {
            spectator.sendMessage(Component.text("That player is not in an active game.", NamedTextColor.RED));
            return;
        }

        leaveGame(spectator, false);

        game.addPlayer(spectator, GameEnums.TeamColor.SPECTATOR);
        playerGames.put(spectator.getUniqueId(), game);
        spectator.sendMessage(Component.text("You are now spectating " + target.getName() + "'s game.", NamedTextColor.AQUA));
    }

    public static void leaveGame(Player player, boolean teleportToLobby) {
        Game game = getPlayerGame(player);
        if (game == null) {
            if (teleportToLobby) Heroes.teleportToLobby(player);
            return;
        }

        // If a player leaves a waiting 2v2/party game, the whole party leaves.
        if (game.gameState() == GameEnums.GameState.WAITING && game.gameMode() != GameEnums.GameMode.ONE_V_ONE && PartyManager.isInParty(player)) {
            Set<Player> partyMembers = PartyManager.getPartyMembers(player);
            partyMembers.forEach(member -> {
                game.removePlayer(member);
                playerGames.remove(member.getUniqueId());
                if (teleportToLobby) Heroes.teleportToLobby(member);
            });
        } else {
            game.removePlayer(player);
            playerGames.remove(player.getUniqueId());
            if (teleportToLobby) Heroes.teleportToLobby(player);
        }

        if (game.onlinePlayers().isEmpty()) {
            game.end(GameEnums.GameEndReason.FORFEIT);
            waitingGames.remove(game);
        }
    }

    public static Game changeMap(Game oldGame, GameMap newMap) {
        Game newGame = new Game(newMap, oldGame.gameMode());

        newGame.settings().copy(oldGame.settings());
        newGame.setAllTeamSettings(oldGame.defaultTeamSettings());

        Map<Player, GameEnums.TeamColor> playerTeams = new HashMap<>();
        for (Player p : oldGame.onlinePlayers()) {
            playerTeams.put(p, oldGame.playerColor(p));
        }

        for (Map.Entry<Player, GameEnums.TeamColor> entry : playerTeams.entrySet()) {
            Player player = entry.getKey();
            GameEnums.TeamColor teamColor = entry.getValue();

            TeamSettings oldTeamSettings = oldGame.getTeams().get(teamColor).settings();
            newGame.getTeams().get(teamColor).setSettings(oldTeamSettings);

            newGame.addPlayer(player, teamColor);
            playerGames.put(player.getUniqueId(), newGame);
        }

        oldGame.clean();
        return newGame;
    }

    private static void handle1v1Join(Player player) {
        if (PartyManager.isInParty(player)) {
            player.sendMessage(Component.text("You cannot join 1v1 while in a party.", NamedTextColor.RED));
            return;
        }

        Game game = findGame(GameEnums.GameMode.ONE_V_ONE);

        if(game == null) {
            game = createGame(GameEnums.GameMode.ONE_V_ONE);
            game.addPlayer(player, GameEnums.TeamColor.RED);
        } else {
            game.addPlayer(player, GameEnums.TeamColor.BLUE);
        }

        playerGames.put(player.getUniqueId(), game);

        if (game.canStart()) {
            waitingGames.remove(game);
            game.start();
        }
    }

    private static void handle2v2Join(Player player) {
        Party party = PartyManager.getPlayerParty(player);
        if (party == null || party.getSize() != 2) {
            player.sendMessage(Component.text("You must be in a party of 2 to join 2v2.", NamedTextColor.RED));
            return;
        }

        Game game = findGame(GameEnums.GameMode.TWO_V_TWO);
        GameEnums.TeamColor teamColor;

        if(game == null) {
            teamColor = GameEnums.TeamColor.RED;
            game = createGame(GameEnums.GameMode.TWO_V_TWO);
        } else {
            teamColor = GameEnums.TeamColor.BLUE;
        }

        // Add both party members to the game
        Game finalGame = game;
        PartyManager.getPartyMembers(player).forEach(member -> {
            finalGame.addPlayer(member, teamColor);
            playerGames.put(member.getUniqueId(), finalGame);
        });

        if (game.canStart()) {
            waitingGames.remove(game);
            game.start();
        }
    }

    private static void handlePartyJoin(Player player) {
        Party party = PartyManager.getPlayerParty(player);
        if (party == null) {
            player.sendMessage(Component.text("You must be in a party to start a party game.", NamedTextColor.RED));
            return;
        }
        if (party.getSize() > 18) {
            player.sendMessage(Component.text("Your party is too large for this mode (max 18 players).", NamedTextColor.RED));
            return;
        }

        Game game = new Game(GameMapManager.getRandomMapInAll(Set.of(GameEnums.GameMode.ONE_V_ONE, GameEnums.GameMode.TWO_V_TWO)), GameEnums.GameMode.PARTY);

        List<Player> members = new ArrayList<>(PartyManager.getPartyMembers(player));
        members.forEach(member -> playerGames.put(member.getUniqueId(), game));

        members.remove(player);
        game.addPlayer(player, GameEnums.TeamColor.RED);
        if (!members.isEmpty()) game.addPlayer(members.getFirst(), GameEnums.TeamColor.BLUE);

        for (int i = 1; i < members.size(); i++) {
            game.addPlayer(members.get(i), GameEnums.TeamColor.SPECTATOR);
        }

        GUIManager.open(player, new PartyModeGUI(game, player));
    }

    private static Game findGame(GameEnums.GameMode gameMode) {
        return waitingGames.stream()
                .filter(g -> g.gameMode() == gameMode)
                .findFirst()
                .orElse(null);
    }
    private static Game createGame(GameEnums.GameMode gameMode) {
        Game newGame = new Game(GameMapManager.getRandomMap(gameMode), gameMode);
        waitingGames.add(newGame);
        return newGame;
    }


    // Utility

    public static Game getPlayerGame(Player player) {
        if (player == null) return null;
        return playerGames.get(player.getUniqueId());
    }

    public static boolean isPlayerInGame(Player player) {
        return player != null && playerGames.containsKey(player.getUniqueId());
    }

    public static void clear() {
        new HashSet<>(playerGames.values()).forEach(Game::clean);
        playerGames.clear();
        waitingGames.clear();
    }
}
