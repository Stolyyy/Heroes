package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameManager;
import me.stolyy.heroes.game.GameState;
import me.stolyy.heroes.game.Team;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class SpectateGUI extends GUI {
    private static final int MAX_CONTENT_SLOTS = 45; // first 5 rows
    private static final int SLOT_CLOSE = 49;
    private static final long CYCLE_PERIOD_TICKS = 40L;

    private final Map<Integer, Game> slotToGame = new HashMap<>();
    private final Map<Integer, List<Player>> gamePlayers = new HashMap<>();
    private final Map<Integer, Integer> currentPlayerIndex = new HashMap<>();
    private BukkitTask cycleTask;

    public SpectateGUI(Player player) {
        super(player, 54, "Spectate Games");
        startCycleTask();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();

        if (slot == SLOT_CLOSE) {
            cleanup();
            GUIManager.close(player);
            return;
        }

        Game game = slotToGame.get(slot);
        if (game == null) return;

        List<Player> players = gamePlayers.get(slot);
        if (players == null || players.isEmpty()) return;

        cleanup();
        GUIManager.close(player);
        GameManager.spectateGame(player, players.getFirst());
    }

    @Override
    protected void populate() {
        Set<Game> games = GameManager.getActiveGames();
        int slot = 0;

        for (Game game : games) {
            // STARTING games are watchable too — join during the countdown, see the whole round.
            if (game.gameState() != GameState.IN_PROGRESS
                    && game.gameState() != GameState.STARTING) continue;
            if (slot >= MAX_CONTENT_SLOTS) break;

            List<Player> players = new ArrayList<>(game.onlinePlayers(false));
            if (players.isEmpty()) continue;

            slotToGame.put(slot, game);
            gamePlayers.put(slot, players);
            currentPlayerIndex.put(slot, 0);

            inventoryItems.put(slot, createGameItem(game, players.getFirst()));
            slot++;
        }

        inventoryItems.put(SLOT_CLOSE, ItemBuilder.from(Material.PAPER)
                .name("<red>Close")
                .modelData("gui", "general", "close")
                .build());
    }

    private ItemStack createGameItem(Game game, Player displayPlayer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        meta.setOwningPlayer(displayPlayer);
        String starting = game.gameState() == GameState.STARTING ? " (Starting...)" : "";
        meta.displayName(Component.text(game.gameMode().displayName() + " Game" + starting, NamedTextColor.GOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        for (TeamColor teamColor : List.of(TeamColor.RED, TeamColor.BLUE)) {
            Team team = game.getTeams().get(teamColor);
            if (team == null || team.onlinePlayers().isEmpty()) continue;

            lore.add(Component.text(teamColor.displayName() + " Team:", teamColor.textColor()));
            for (Player p : team.onlinePlayers()) {
                int elo = PlayerStatsManager.getStats(p).getElo(game.gameMode());
                lore.add(Component.text("  " + p.getName() + " ", NamedTextColor.WHITE)
                        .append(Component.text("(" + elo + ")", NamedTextColor.GRAY)));
            }
        }

        Team spectators = game.getTeams().get(TeamColor.SPECTATOR);
        if (spectators != null && !spectators.onlinePlayers().isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.text("Spectators:", NamedTextColor.GRAY));
            for (Player p : spectators.onlinePlayers()) {
                lore.add(Component.text("  " + p.getName(), NamedTextColor.DARK_GRAY));
            }
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click to spectate!", NamedTextColor.YELLOW));

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private void startCycleTask() {
        cycleTask = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            boolean updated = false;
            for (int slot : gamePlayers.keySet()) {
                List<Player> players = gamePlayers.get(slot);
                if (players.size() <= 1) continue;

                int index = (currentPlayerIndex.get(slot) + 1) % players.size();
                currentPlayerIndex.put(slot, index);

                Game game = slotToGame.get(slot);
                if (game != null) {
                    inventoryItems.put(slot, createGameItem(game, players.get(index)));
                    updated = true;
                }
            }
            if (updated) {
                update();
            }
        }, CYCLE_PERIOD_TICKS, CYCLE_PERIOD_TICKS);
    }

    @Override
    public void cleanup() {
        if (cycleTask != null && !cycleTask.isCancelled()) {
            cycleTask.cancel();
        }
    }
}
