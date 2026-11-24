package me.stolyy.heroes.vfx.ui.gui.settings;

import me.stolyy.heroes.game.Game;
import me.stolyy.heroes.game.GameBot;
import me.stolyy.heroes.game.GamePlayer;
import me.stolyy.heroes.game.TeamColor;
import me.stolyy.heroes.game.rules.BossSetup;
import me.stolyy.heroes.vfx.effect.ItemBuilder;
import me.stolyy.heroes.vfx.effect.SoundBuilder;
import me.stolyy.heroes.vfx.ui.GuiText;
import me.stolyy.heroes.vfx.ui.gui.CustomGameGUI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boss-mode setup: participant heads on the top row designate the boss; the settings row tunes
 * boss HP, non-boss lives/HP, lives-per-enemy multiplier, and random roll on {@link Game#bossConfig()}.
 */
public class BossModeSettingsGUI extends SettingsGUI {
    private static final int HEAD_ROW_START = 0;
    private static final int SLOT_BOSS_HP_PER_LIFE = 9;
    private static final int SLOT_NON_BOSS_LIVES = 11;
    private static final int SLOT_NON_BOSS_HP = 13;
    private static final int SLOT_LIVES_PER_ENEMY = 15;
    private static final int SLOT_RANDOM_BOSS = 17;
    private static final int SLOT_BACK = 22;

    private static final double[] LIVES_PER_ENEMY_CYCLE = {0.5, 1.0, 1.5, 2.0, 3.0};
    private static final double[] BOSS_HP_CYCLE = {100.0, 200.0, 300.0, 500.0};
    private static final int[] NON_BOSS_LIVES_CYCLE = {1, 2, 3, 4};
    private static final double[] NON_BOSS_HP_CYCLE = {50.0, 100.0, 150.0, 200.0};

    private final Map<Integer, UUID> headSlots = new HashMap<>();

    public BossModeSettingsGUI(Game game, Player player, CustomGameGUI partyGUI) {
        super(game, player, 27, GuiText.raw("gui.settings.boss.title"), partyGUI, SLOT_BACK, false);

        Target<BossSetup.Config> target = new Target<>(
                game::bossConfig,
                op -> {
                    game.updateBossConfig(op);
                    BossSetup.reapplyIfDesignated(game);
                });

        addRow(Row.doubleValue(SLOT_BOSS_HP_PER_LIFE, target,
                new Lens<>(BossSetup.Config::bossHpPerLife, BossSetup.Config::withBossHpPerLife),
                "gui.settings.boss.hp-per-life", Material.ENCHANTED_GOLDEN_APPLE, null,
                BOSS_HP_CYCLE, 10, 1000, false,
                v -> Map.of("value", String.valueOf((int) v.doubleValue()))));

        addRow(Row.intValue(SLOT_NON_BOSS_LIVES, target,
                new Lens<>(BossSetup.Config::nonBossLives, BossSetup.Config::withNonBossLives),
                "gui.settings.boss.non-boss-lives", Material.PAPER, NON_BOSS_LIVES_CYCLE, 1, 20));

        addRow(Row.doubleValue(SLOT_NON_BOSS_HP, target,
                new Lens<>(BossSetup.Config::nonBossHp, BossSetup.Config::withNonBossHp),
                "gui.settings.boss.non-boss-hp", Material.GOLDEN_APPLE, null,
                NON_BOSS_HP_CYCLE, 10, 500, false,
                v -> Map.of("value", String.valueOf((int) v.doubleValue()))));

        addRow(Row.doubleValue(SLOT_LIVES_PER_ENEMY, target,
                new Lens<>(BossSetup.Config::livesPerEnemy, BossSetup.Config::withLivesPerEnemy),
                "gui.settings.boss.lives-per-enemy", Material.TOTEM_OF_UNDYING, null,
                LIVES_PER_ENEMY_CYCLE, 0.1, 10.0, true,
                v -> Map.of("value", BossSetup.formatLives(v), "summary", bossLivesSummary(v))));

        addRow(Row.action(SLOT_RANDOM_BOSS, this::renderRandomBoss, this::handleRandomBoss));
    }

    @Override
    protected void renderExtra() {
        headSlots.clear();
        int slot = HEAD_ROW_START;
        for (GamePlayer gamePlayer : game.getGamePlayers().values()) {
            if (slot >= HEAD_ROW_START + 9) break;
            if (gamePlayer.leftGame() || gamePlayer.team() == TeamColor.SPECTATOR) continue;
            ItemStack head = createParticipantHead(gamePlayer);
            if (head == null) continue;
            if (gamePlayer.uuid().equals(game.bossPlayer())) highlightItem(head);
            inventoryItems.put(slot, head);
            headSlots.put(slot, gamePlayer.uuid());
            slot++;
        }
    }

    @Override
    protected boolean handleExtraClick(int slot, ClickType click) {
        UUID clicked = headSlots.get(slot);
        if (clicked == null) return false;
        designateBoss(clicked);
        return true;
    }

    private ItemStack createParticipantHead(GamePlayer gamePlayer) {
        boolean isBoss = gamePlayer.uuid().equals(game.bossPlayer());
        List<Component> lore = GuiText.lore(isBoss
                ? "gui.settings.boss.head-boss-lore"
                : "gui.settings.boss.head-pick-lore");

        Player target = Bukkit.getPlayer(gamePlayer.uuid());
        if (target != null) {
            return playerHead(target, lore);
        }
        GameBot bot = gamePlayer.bot();
        if (bot == null) return null;
        return botHead(bot, lore);
    }

    private ItemStack renderRandomBoss() {
        return ItemBuilder.from(Material.CHORUS_FRUIT)
                .name(GuiText.component("gui.settings.boss.random-boss.name"))
                .lore(GuiText.lore("gui.settings.boss.random-boss.lore", "current", bossNameSummary()))
                .build();
    }

    private void handleRandomBoss(SettingsGUI host, ClickType click) {
        UUID picked = BossSetup.rollBossPlayer(game);
        if (picked == null) {
            host.playError();
            return;
        }
        designateBoss(picked);
    }

    private void designateBoss(UUID bossUuid) {
        BossSetup.designate(game, bossUuid);
        SoundBuilder.of(Sound.ENTITY_ENDER_DRAGON_GROWL).volume(0.5f).pitch(1.5f).play(player);
        update();
    }

    private String bossNameSummary() {
        if (game.bossPlayer() == null) {
            return GuiText.raw("gui.settings.boss.random-boss.current-none");
        }
        Player target = Bukkit.getPlayer(game.bossPlayer());
        if (target != null) {
            return GuiText.raw("gui.settings.boss.random-boss.current-player", "name", target.getName());
        }
        GameBot bot = game.getGamePlayer(game.bossPlayer()).bot();
        return bot == null
                ? GuiText.raw("gui.settings.boss.random-boss.current-unknown")
                : GuiText.raw("gui.settings.boss.random-boss.current-bot", "name", bot.getName());
    }

    private String bossLivesSummary(double livesPerEnemy) {
        if (game.bossPlayer() == null) {
            return GuiText.raw("gui.settings.boss.lives-per-enemy.summary-unpicked");
        }
        int enemies = BossSetup.enemyCount(game, game.bossPlayer());
        return GuiText.raw("gui.settings.boss.lives-per-enemy.summary-picked",
                "lives", BossSetup.bossLives(enemies, livesPerEnemy),
                "enemies", enemies);
    }
}
