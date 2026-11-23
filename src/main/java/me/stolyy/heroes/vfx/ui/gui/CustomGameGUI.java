package me.stolyy.heroes.vfx.ui.gui;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.*;
import me.stolyy.heroes.game.map.GameMap;
import me.stolyy.heroes.game.map.GameMapManager;
import me.stolyy.heroes.game.series.MatchSeries;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.progression.PlayerStats;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.BotNameManager;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.npc.types.GameBotNpc;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Custom game lobby GUI.
 *
 * <p>Layout (9x6): row 0 is the spectator strip — up to 7 spectator heads (6 on later
 * pages, where slot 0 becomes a left arrow), slot 7 is a right arrow while more pages
 * remain (add-bot otherwise), slot 8 is auto-balance. One column (game-config.yml
 * {@code custom-gui.utility-column}, default 8) holds the utilities top-to-bottom:
 * gamemode, game settings, map, start, cancel. The remaining 8 columns are teams in
 * {@link TeamColor} enum order — header wool on row 1, four member rows below. Teams
 * with more than 4 members rotate their visible window every couple of seconds.
 */
public class CustomGameGUI extends GUI {
    private static final long TICKER_PERIOD = 40L; // 2s — overfull-team rotation + live refresh
    private static final int SLOT_ARROW_OR_BOT = 7;
    private static final int SLOT_BALANCE = 8;
    private static final int PAGE0_CAP = 7; // spectator heads on page 0 (slots 0-6)
    private static final int PAGEN_CAP = 6; // later pages: slot 0 is the left arrow

    private enum BalanceShape {
        FFA("Free-for-all"),
        TWO_TEAMS("Two Teams"),
        TEAMS_OF_TWO("Teams of 2");

        final String label;
        BalanceShape(String label) { this.label = label; }
        BalanceShape next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private Game game;
    private final Random random = new Random();

    private final int utilityColumn;
    private final Map<Integer, TeamColor> headerSlots = new LinkedHashMap<>();
    private final Map<Integer, TeamColor> spotTeam = new LinkedHashMap<>(); // member slot -> team
    private final int slotGamemode, slotSettings, slotMap, slotStart, slotCancel;

    // Per-render state
    private final Map<Integer, UUID> slotOwners = new HashMap<>();
    private boolean leftArrowShown = false;
    private boolean rightArrowShown = false;

    private UUID selectedUuid = null;
    private int spectatorPage = 0;
    private int cycleStep = 0;
    private BalanceShape balanceShape = BalanceShape.FFA;
    private GameMapGUI.MapFilter poolFilter = null; // null = Auto (roster-based pool)
    private BukkitTask ticker = null;

    public CustomGameGUI(Game game, Player player) {
        super(player, 54, "Custom Game Setup");
        this.game = game;

        this.utilityColumn = GameConfigManager.getCustomGuiUtilityColumn();
        this.slotGamemode = 9 + utilityColumn;
        this.slotSettings = 18 + utilityColumn;
        this.slotMap      = 27 + utilityColumn;
        this.slotStart    = 36 + utilityColumn;
        this.slotCancel   = 45 + utilityColumn;

        TeamColor[] playable = Arrays.stream(TeamColor.values())
                .filter(c -> c != TeamColor.SPECTATOR)
                .toArray(TeamColor[]::new);
        int t = 0;
        for (int col = 0; col <= 8 && t < playable.length; col++) {
            if (col == utilityColumn) continue;
            TeamColor color = playable[t++];
            headerSlots.put(9 + col, color);
            for (int row = 2; row <= 5; row++) {
                spotTeam.put(row * 9 + col, color);
            }
        }
    }

    @Override
    protected void populate() {
        CustomGameHotbar.remove(player);
        startTicker();
    }

    private void startTicker() {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(Heroes.getInstance(), () -> {
            if (GUIManager.getOpenGui(player) != this) {
                ticker.cancel();
                ticker = null;
                return;
            }
            cycleStep++;
            update();
        }, TICKER_PERIOD, TICKER_PERIOD);
    }

    @Override
    protected void update() {
        inventoryItems.clear();
        slotOwners.clear();

        renderSpectatorRow();
        renderBalanceButton();
        renderUtilities();
        renderTeams();

        super.update();
    }

    // ---------- rendering ----------

    private void renderSpectatorRow() {
        List<UUID> spectators = orderedMembers(game.getTeams().get(TeamColor.SPECTATOR));

        // Clamp the page if the list shrank under us.
        while (spectatorPage > 0 && pageStart(spectatorPage) >= spectators.size()) spectatorPage--;

        int start = pageStart(spectatorPage);
        int cap = spectatorPage == 0 ? PAGE0_CAP : PAGEN_CAP;
        int slot = spectatorPage == 0 ? 0 : 1;

        leftArrowShown = spectatorPage > 0;
        if (leftArrowShown) {
            inventoryItems.put(0, ItemBuilder.from(Material.PAPER)
                    .name("<white>Previous Page")
                    .modelData("gui", "general", "prev")
                    .build());
        }

        int shown = 0;
        for (int i = start; i < spectators.size() && shown < cap; i++, shown++) {
            UUID uuid = spectators.get(i);
            inventoryItems.put(slot, headFor(uuid));
            slotOwners.put(slot, uuid);
            slot++;
        }

        rightArrowShown = start + shown < spectators.size();
        if (rightArrowShown) {
            inventoryItems.put(SLOT_ARROW_OR_BOT, ItemBuilder.from(Material.PAPER)
                    .name("<white>Next Page")
                    .lore(Component.text((spectators.size() - start - shown) + " more spectators", NamedTextColor.GRAY))
                    .modelData("gui", "general", "next")
                    .build());
        } else {
            inventoryItems.put(SLOT_ARROW_OR_BOT, ItemBuilder.from(Material.PAPER)
                    .name("<green>Add Bot")
                    .lore(Component.text("Adds a random-hero bot", NamedTextColor.GRAY),
                          Component.text("to the smallest team.", NamedTextColor.GRAY))
                    .modelData("gui", "customgame", "add-bot")
                    .build());
        }
    }

    private int pageStart(int page) {
        return page == 0 ? 0 : PAGE0_CAP + (page - 1) * PAGEN_CAP;
    }

    private void renderBalanceButton() {
        int n = participants().size();
        inventoryItems.put(SLOT_BALANCE, ItemBuilder.from(Material.PAPER)
                .name("<aqua>Auto Balance: " + balanceShape.label)
                .lore(Component.text(balancePreview(n), NamedTextColor.GRAY),
                      Component.text("Balanced by elo (avg 1v1/2v2)", NamedTextColor.DARK_GRAY),
                      Component.text("Left-click: apply", NamedTextColor.YELLOW),
                      Component.text("Right-click: change mode", NamedTextColor.YELLOW))
                .modelData("gui", "customgame", "balance")
                .build());
    }

    private void renderUtilities() {
        inventoryItems.put(slotGamemode, ItemBuilder.from(Material.COMPASS)
                .name("<gold>Game Mode: " + game.gameModePreset().displayName())
                .lore(Component.text("Click to change!", NamedTextColor.GRAY))
                .modelData("gui", "customgame", "gamemode")
                .build());
        inventoryItems.put(slotSettings, ItemBuilder.from(Material.PAPER)
                .name("<white>Game Settings")
                .modelData("gui", "customgame", "settings")
                .build());

        GameMap current = game.settings() != null ? game.settings().map() : null;
        inventoryItems.put(slotMap, ItemBuilder.from(Material.OAK_SIGN)
                .name("<gold>Map: " + (current != null ? current.name() : "None"))
                .lore(Component.text("Pool: " + poolLabel(), NamedTextColor.AQUA),
                      Component.text("Left-click: choose map", NamedTextColor.GRAY),
                      Component.text("Right-click: random map from pool", NamedTextColor.GRAY),
                      Component.text("Shift+right-click: change pool", NamedTextColor.GRAY))
                .modelData("gui", "settings", "map")
                .build());

        inventoryItems.put(slotStart, ItemBuilder.from(Material.PAPER)
                .name("<white>Start Game!")
                .modelData("gui", "customgame", "start")
                .build());
        inventoryItems.put(slotCancel, ItemBuilder.from(Material.PAPER)
                .name("<white>Cancel")
                .modelData("gui", "general", "close")
                .build());
    }

    private void renderTeams() {
        for (Map.Entry<Integer, TeamColor> header : headerSlots.entrySet()) {
            TeamColor color = header.getValue();
            List<UUID> members = orderedMembers(game.getTeams().get(color));

            ItemBuilder headerItem = ItemBuilder.from(color.wool())
                    .name("<white>" + color.displayName() + " Team")
                    .modelData("gui", "team", color.name().toLowerCase());
            List<Component> headerLore = new ArrayList<>();
            if (members.size() > 4) {
                headerLore.add(Component.text(members.size() + " members - rotating", NamedTextColor.GRAY));
            }
            if (selectedUuid != null) {
                headerLore.add(Component.text("Click to move selected player here", NamedTextColor.YELLOW));
            }
            if (!headerLore.isEmpty()) {
                headerItem.lore(headerLore.toArray(new Component[0]));
            }
            inventoryItems.put(header.getKey(), headerItem.build());

            // Member slots for this column, top to bottom.
            List<Integer> slots = new ArrayList<>();
            for (Map.Entry<Integer, TeamColor> e : spotTeam.entrySet()) {
                if (e.getValue() == color) slots.add(e.getKey());
            }
            Collections.sort(slots);

            if (members.size() <= slots.size()) {
                int i = 0;
                for (; i < members.size(); i++) {
                    UUID uuid = members.get(i);
                    inventoryItems.put(slots.get(i), headFor(uuid));
                    slotOwners.put(slots.get(i), uuid);
                }
                for (; i < slots.size(); i++) {
                    inventoryItems.put(slots.get(i), createEmptySlot(color));
                }
            } else {
                // Overfull: rotate the visible window one row per ticker step.
                int offset = cycleStep % members.size();
                for (int i = 0; i < slots.size(); i++) {
                    UUID uuid = members.get((offset + i) % members.size());
                    inventoryItems.put(slots.get(i), headFor(uuid));
                    slotOwners.put(slots.get(i), uuid);
                }
            }
        }
    }

    /** Online players first (by name), then bots (by name) — a stable render order. */
    private List<UUID> orderedMembers(Team team) {
        Map<UUID, GameBot> bots = game.getGameBots();
        List<UUID> players = new ArrayList<>();
        List<UUID> botIds = new ArrayList<>();
        for (UUID uuid : team.playerUUIDs()) {
            if (Bukkit.getPlayer(uuid) != null) players.add(uuid);
            else if (bots.containsKey(uuid)) botIds.add(uuid);
        }
        players.sort(Comparator.comparing(u -> Bukkit.getPlayer(u).getName()));
        botIds.sort(Comparator.comparing(u -> bots.get(u).getName()));
        players.addAll(botIds);
        return players;
    }

    private ItemStack headFor(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        ItemStack head = p != null ? createPlayerHead(p) : createBotHead(game.getGameBots().get(uuid));
        if (uuid.equals(selectedUuid)) highlightItem(head);
        return head;
    }

    private ItemStack createEmptySlot(TeamColor color) {
        return ItemBuilder.from(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name("<" + color.name().toLowerCase() + ">Empty Slot")
                .modelData("gui", "team", color.name().toLowerCase() + "-empty")
                .build();
    }

    private ItemStack createBotHead(GameBot bot) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("[BOT] " + bot.getName(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text(bot.getHeroName(), NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false)
            ));
            head.setItemMeta(meta);
        }
        return head;
    }

    // ---------- clicks ----------

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot < 0 || slot >= inventory.getSize()) return;

        if (slot == 0 && leftArrowShown) {
            spectatorPage--;
            clickSound();
            update();
            return;
        }
        if (slot == SLOT_ARROW_OR_BOT) {
            if (rightArrowShown) {
                spectatorPage++;
                clickSound();
                update();
            } else {
                handleAddBot();
            }
            return;
        }
        if (slot == SLOT_BALANCE) {
            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                balanceShape = balanceShape.next();
                clickSound();
                update();
            } else {
                applyBalance();
            }
            return;
        }
        if (slot == slotGamemode) {
            GUIManager.open(player, new GameModeGUI(game, player, this));
            return;
        }
        if (slot == slotSettings) {
            GUIManager.open(player, new GameSettingsGUI(game, player, this));
            return;
        }
        if (slot == slotMap) {
            handleMapClick(event.getClick());
            return;
        }
        if (slot == slotStart) {
            startGame();
            return;
        }
        if (slot == slotCancel) {
            cancelGame();
            return;
        }

        TeamColor headerColor = headerSlots.get(slot);
        if (headerColor != null) {
            if (selectedUuid != null) {
                moveEntity(selectedUuid, headerColor);
                selectedUuid = null;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                update();
            } else {
                GUIManager.open(player, new PlayerSettingsGUI(game.getTeams().get(headerColor), player, this));
            }
            return;
        }

        handleMemberSlotClick(slot, event.getClick());
    }

    /** Shared by the Start button and the close-to-type hotbar item. */
    void startGame() {
        if (game.canStart()) {
            if (game.gameModePreset().isSeries()) {
                if (!game.getGameBots().isEmpty()) {
                    player.sendMessage(Component.text("Series modes don't support bots yet — remove them first.", NamedTextColor.RED));
                    return;
                }
                new MatchSeries(game); // adopts this game as round 1 and starts it
            } else {
                game.start();
            }
            CustomGameHotbar.remove(player);
            GUIManager.close(player);
        } else {
            player.sendMessage(Component.text("Cannot Start! Check Team sizes.", NamedTextColor.RED));
        }
    }

    /** Shared by the Cancel button and the close-to-type hotbar item. */
    void cancelGame() {
        // Close FIRST: leaveGame teleports to the lobby, which fires an InventoryCloseEvent
        // — closing after would let the close handler act on this GUI mid-teardown.
        CustomGameHotbar.remove(player);
        GUIManager.close(player);
        GameManager.leaveGame(player, true);
    }

    private void handleMemberSlotClick(int slot, ClickType clickType) {
        UUID uuid = slotOwners.get(slot);

        // Right-click on a bot head opens BotSettingsGUI; on a player head, that player's
        // per-player settings.
        if (clickType == ClickType.RIGHT && uuid != null) {
            GameBot bot = game.getGameBots().get(uuid);
            if (bot != null) {
                GUIManager.open(player, new BotSettingsGUI(game, bot, uuid, player, this));
            } else {
                GUIManager.open(player, new PlayerSettingsGUI(game, uuid, player, this));
            }
            return;
        }

        if (uuid != null) {
            if (uuid.equals(selectedUuid)) {
                selectedUuid = null;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            } else if (selectedUuid == null) {
                selectedUuid = uuid;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            } else {
                // Swap teams with the clicked entity.
                TeamColor a = game.playerColor(selectedUuid);
                TeamColor b = game.playerColor(uuid);
                if (a != b) {
                    moveEntity(selectedUuid, b);
                    moveEntity(uuid, a);
                }
                selectedUuid = null;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
            update();
            return;
        }

        // Empty team spot: move the selection there. Row 0 (slots 0-6 on page 0) is the
        // spectator strip and isn't in spotTeam, so an empty spot there falls back to SPECTATOR.
        TeamColor target = spotTeam.get(slot);
        if (target == null && slot < PAGE0_CAP) target = TeamColor.SPECTATOR;
        if (target != null && selectedUuid != null) {
            moveEntity(selectedUuid, target);
            selectedUuid = null;
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            update();
        }
    }

    private void moveEntity(UUID uuid, TeamColor target) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) game.changeTeam(p, target);
        else if (game.getGameBots().containsKey(uuid)) game.changeBotTeam(uuid, target);
    }

    private void clickSound() {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    // ---------- auto balance ----------

    /** Every online player and bot in the game, spectators included. */
    private List<UUID> participants() {
        List<UUID> all = new ArrayList<>();
        for (Team team : game.getTeams().values()) {
            all.addAll(orderedMembers(team));
        }
        return all;
    }

    private int eloOf(UUID uuid) {
        if (game.getGameBots().containsKey(uuid)) return 1000;
        PlayerStats stats = PlayerStatsManager.getStats(uuid);
        return (stats.getElo(GameMode.ONE_V_ONE) + stats.getElo(GameMode.TWO_V_TWO)) / 2;
    }

    private String balancePreview(int n) {
        if (n == 0) return "No players yet";
        return switch (balanceShape) {
            case FFA -> n <= 8
                    ? n + " players -> " + n + " solo teams"
                    : n + " players -> 8 solo teams + " + (n - 8) + " spectating";
            case TWO_TEAMS -> n + " players -> " + (n - n / 2) + "v" + (n / 2);
            case TEAMS_OF_TWO -> {
                List<String> sizes = new ArrayList<>();
                int remaining = Math.min(n, 16);
                while (remaining > 0 && sizes.size() < 8) {
                    sizes.add(String.valueOf(Math.min(2, remaining)));
                    remaining -= 2;
                }
                String shape = String.join("v", sizes);
                yield n > 16 ? n + " players -> " + shape + " + " + (n - 16) + " spectating"
                             : n + " players -> " + shape;
            }
        };
    }

    private void applyBalance() {
        List<UUID> parts = participants();
        if (parts.isEmpty()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1.0f);
            return;
        }
        parts.sort(Comparator.comparingInt(this::eloOf).reversed());

        TeamColor[] order = Arrays.stream(TeamColor.values())
                .filter(c -> c != TeamColor.SPECTATOR)
                .toArray(TeamColor[]::new);

        Map<UUID, TeamColor> target = new HashMap<>();
        switch (balanceShape) {
            case FFA -> {
                for (int i = 0; i < parts.size(); i++) {
                    target.put(parts.get(i), i < order.length ? order[i] : TeamColor.SPECTATOR);
                }
            }
            case TWO_TEAMS -> {
                // Elo snake draft: R,B,B,R,R,B,B,... keeps the sides even.
                for (int i = 0; i < parts.size(); i++) {
                    boolean red = i % 4 == 0 || i % 4 == 3;
                    target.put(parts.get(i), red ? order[0] : order[1]);
                }
            }
            case TEAMS_OF_TWO -> {
                // Pair strongest with weakest so pairs come out even.
                int lo = 0, hi = parts.size() - 1, team = 0;
                while (lo <= hi && team < order.length) {
                    target.put(parts.get(lo), order[team]);
                    if (hi > lo) target.put(parts.get(hi), order[team]);
                    lo++;
                    hi--;
                    team++;
                }
                for (; lo <= hi; lo++) target.put(parts.get(lo), TeamColor.SPECTATOR);
            }
        }

        target.forEach(this::moveEntity);
        selectedUuid = null;
        spectatorPage = 0;
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        update();
    }

    // ---------- map ----------

    private void handleMapClick(ClickType click) {
        switch (click) {
            case SHIFT_RIGHT -> cyclePool();
            case RIGHT -> rerollMap();
            default -> GUIManager.open(player, new GameMapGUI(game, player, this));
        }
    }

    /** Advances Auto -> 1v1 Only -> 2v2 Only -> 1v1 AND 2v2 -> 1v1 OR 2v2 -> All -> Auto. */
    private void cyclePool() {
        GameMapGUI.MapFilter[] filters = GameMapGUI.MapFilter.values();
        if (poolFilter == null) {
            poolFilter = filters[0];
        } else if (poolFilter.ordinal() == filters.length - 1) {
            poolFilter = null;
        } else {
            poolFilter = filters[poolFilter.ordinal() + 1];
        }
        clickSound();
        update();
    }

    private String poolLabel() {
        return poolFilter == null ? "Auto (" + autoPoolDescriptor() + ")" : poolFilter.getDisplayName();
    }

    private List<GameMap> poolMaps() {
        Set<GameMap> pool = poolFilter != null ? poolFilter.filterMaps() : autoPool();
        List<GameMap> list = new ArrayList<>(pool);
        list.sort(Comparator.comparing(GameMap::name));
        return list;
    }

    /**
     * Auto pool from the current roster: 2 or fewer seated players use the 1v1 pool;
     * 3-4 seated NOT in a 2v2 shape need maps in BOTH pools; an exact 2v2 (or more than 4
     * seated) uses the 2v2 pool.
     */
    private Set<GameMap> autoPool() {
        return switch (autoPoolDescriptor()) {
            case "1v1" -> GameMapManager.getMapsForMode(GameMode.ONE_V_ONE);
            case "2v2" -> GameMapManager.getMapsForMode(GameMode.TWO_V_TWO);
            default -> {
                Set<GameMap> both = new LinkedHashSet<>(GameMapManager.getMapsForMode(GameMode.ONE_V_ONE));
                both.retainAll(GameMapManager.getMapsForMode(GameMode.TWO_V_TWO));
                yield both;
            }
        };
    }

    private String autoPoolDescriptor() {
        List<Integer> sizes = new ArrayList<>();
        for (Team team : game.getTeams().values()) {
            if (team.color() == TeamColor.SPECTATOR) continue;
            int size = orderedMembers(team).size();
            if (size > 0) sizes.add(size);
        }
        int seated = sizes.stream().mapToInt(Integer::intValue).sum();
        boolean is2v2 = sizes.size() == 2 && sizes.get(0) == 2 && sizes.get(1) == 2;

        if (seated <= 2) return "1v1";
        if (!is2v2 && seated <= 4) return "1v1 & 2v2";
        return "2v2";
    }

    private void rerollMap() {
        List<GameMap> pool = poolMaps();
        GameMap current = game.settings().map();
        pool.removeIf(m -> current != null && m.name().equals(current.name()));
        if (pool.isEmpty()) {
            player.sendMessage(Component.text("No other maps in this pool.", NamedTextColor.RED));
            return;
        }
        applyMap(pool.get(random.nextInt(pool.size())));
    }

    private void applyMap(GameMap map) {
        game = GameManager.changeMap(game, map);
        player.sendMessage(Component.text("Map changed to: " + map.name(), NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        update();
    }

    // ---------- bots ----------

    private void handleAddBot() {
        if (game.getGameBots().size() >= GameConfigManager.getMaxBotsPerGame()) {
            player.sendMessage(Component.text("This game has reached the bot limit.", NamedTextColor.RED));
            return;
        }

        Set<String> usedNames = new HashSet<>();
        for (GameBot bot : game.getGameBots().values()) {
            usedNames.add(bot.getName());
        }
        String botName = BotNameManager.getRandomName(usedNames);

        // Bots never roll heroes they can't play (bots.yml disabled_heroes — greenhood's bow
        // flow is player-packet-driven). Fall back to the unfiltered list rather than "default"
        // if someone disables the whole roster.
        List<String> availableHeroes = HeroConfig.getAvailableNames().stream()
                .filter(name -> !me.stolyy.heroes.util.BotConfig.isDisabledForBots(name))
                .toList();
        if (availableHeroes.isEmpty()) availableHeroes = HeroConfig.getAvailableNames();
        String heroName;
        if (!availableHeroes.isEmpty()) {
            heroName = availableHeroes.get(random.nextInt(availableHeroes.size()));
        } else {
            heroName = "default";
        }

        Location botSpawnLoc;
        if (game.settings() != null && game.settings().map() != null
                && !game.settings().map().spawnLocations().isEmpty()) {
            botSpawnLoc = game.settings().map().spawnLocations().get(0);
        } else {
            botSpawnLoc = player.getLocation();
        }

        GameBotNpc npc = new GameBotNpc(botName, botSpawnLoc);
        npc.spawn();

        if (npc.getHandle() == null) {
            player.sendMessage(Component.text("Bot support requires an NPC plugin (Citizens).", NamedTextColor.RED));
            return;
        }

        GameBot bot = new GameBot(botName, heroName, npc.getHandle());
        if (!game.addBot(bot, getSmallestTeam(game))) {
            // addBot mutated nothing, so the NPC we just spawned belongs to no game — destroy it
            // rather than leave it standing in the arena attached to nothing.
            player.sendMessage(Component.text("Could not add the bot to the game.", NamedTextColor.RED));
            NpcServiceProvider.get().remove(npc.getHandle().id());
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        update();
    }

    private static TeamColor getSmallestTeam(Game game) {
        TeamColor smallest = TeamColor.RED;
        int minSize = Integer.MAX_VALUE;
        Map<UUID, GameBot> bots = game.getGameBots();
        for (Map.Entry<TeamColor, Team> entry : game.getTeams().entrySet()) {
            TeamColor color = entry.getKey();
            if (color == TeamColor.SPECTATOR) continue;
            Team team = entry.getValue();
            int size = (int) team.playerUUIDs().stream()
                    .filter(uuid -> Bukkit.getPlayer(uuid) != null || bots.containsKey(uuid))
                    .count();
            if (size < minSize) {
                minSize = size;
                smallest = color;
            }
        }
        return smallest;
    }

    // ---------- plumbing ----------

    public void setGame(Game newGame) {
        this.game = newGame;
    }

    public Game getGame() {
        return game;
    }

    @Override
    public void cleanup() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        // Reached on Escape (the GUI is unlocked so it can close) and on quit; a game that is
        // gone, started, or left by the host gets no hotbar.
        if (game.gameState() == GameState.WAITING && GameManager.getPlayerGame(player) == game) {
            CustomGameHotbar.give(player, this);
        }
    }
}
