package me.stolyy.heroes.game;

import me.stolyy.heroes.ability.Ammo;
import me.stolyy.heroes.ability.Cooldown;
import me.stolyy.heroes.ability.template.BaseAbility;
import me.stolyy.heroes.control.BotController;
import me.stolyy.heroes.control.BotLoadout;
import me.stolyy.heroes.control.Controller;
import me.stolyy.heroes.control.PlayerController;
import me.stolyy.heroes.game.rules.OnKillAction;
import me.stolyy.heroes.hero.Hero;
import me.stolyy.heroes.hero.HeroConfig;
import me.stolyy.heroes.hero.HeroManager;
import me.stolyy.heroes.hero.minion.MinionManager;
import me.stolyy.heroes.npc.NpcServiceProvider;
import me.stolyy.heroes.progression.PlayerStatsManager;
import me.stolyy.heroes.util.ErrorReporter;
import me.stolyy.heroes.util.GameConfigManager;
import me.stolyy.heroes.util.GameModeConfigManager;
import me.stolyy.heroes.vfx.cosmetic.CosmeticManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Initializes participant/bot heroes at match start, manages swaps, and builds Evolution orders. */
final class MatchHeroes {
    private final Game game;
    private final MatchRewards rewards;
    private List<String> sharedEvolutionOrder;

    MatchHeroes(Game game, MatchRewards rewards) {
        this.game = game;
        this.rewards = rewards;
    }

    /** Eligible pool for randomized hero picks: never admin-only, and unlocked when a real player is given. */
    static List<String> eligibleRandomHeroes(@Nullable Player player) {
        return HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name))
                .filter(name -> player == null || HeroManager.isHeroUnlockedFor(player, name))
                .toList();
    }

    void initPlayers() {
        for (Player p : game.onlinePlayers(false)) {
            GamePlayer gamePlayer = game.getGamePlayer(p.getUniqueId());
            gamePlayer.seedLives();
            String heroName;
            if (game.modeConfig().onKill() == OnKillAction.ADVANCE_HERO) {
                initHeroOrder(gamePlayer);
                heroName = gamePlayer.heroOrder().get(0);
            } else {
                heroName = gamePlayer.resolveHeroName();
            }
            if (heroName == null || !HeroConfig.exists(heroName)) {
                List<String> available = eligibleRandomHeroes(p);
                if (available.isEmpty()) {
                    throw new IllegalStateException("Player " + p.getName() + " has no unlocked or valid hero available");
                }
                heroName = available.get(ThreadLocalRandom.current().nextInt(available.size()));
                PlayerStatsManager.getStats(p).setSelectedHero(heroName.toLowerCase());
                p.sendMessage(GameConfigManager.getMessage("game.random-hero-assigned",
                        Map.of("hero", HeroConfig.formatName(heroName))));
            }
            gamePlayer.setCurrentHeroName(heroName);
            rewards.initializePlayerStats(p, heroName);
            Hero hero = HeroManager.createHero(heroName, new PlayerController(p));
            CosmeticManager.apply(hero.getController(), hero);
            game.tickedHeroIds().add(p.getUniqueId());

            GameEffects.restrictPlayer(p);
            GameEffects.applyEffects(p, gamePlayer.settings());
        }
    }

    void initBots() {
        // Iterate a snapshot because the failure handler calls removeBot, which mutates botMap().
        for (Map.Entry<UUID, GameBot> entry : new HashMap<>(game.botMap()).entrySet()) {
            UUID botUUID = entry.getKey();
            GameBot bot = entry.getValue();
            try {
                LivingEntity entity = NpcServiceProvider.get().getBukkitEntity(bot.getNpcHandle());
                if (!(entity instanceof Player botEntity)) {
                    throw new IllegalStateException("NPC entity is missing or not a Player");
                }

                GamePlayer botGamePlayer = game.getGamePlayer(botUUID);
                botGamePlayer.seedLives();
                String botHeroName = bot.getHeroName();
                if (game.modeConfig().onKill() == OnKillAction.ADVANCE_HERO) {
                    initHeroOrder(botGamePlayer);
                    botHeroName = botGamePlayer.heroOrder().get(0);
                }
                botGamePlayer.setCurrentHeroName(botHeroName);

                Hero hero = HeroManager.createHero(botHeroName, new BotController(bot.getNpcHandle()));
                BotLoadout.configure(botUUID, bot, hero, botHeroName);

                game.aliveIds().add(botUUID);
                game.tickedHeroIds().add(botUUID);
                Team botTeam = game.playerTeam(botUUID);
                game.originalTeamMap().put(botUUID, botTeam.color());

                CosmeticManager.apply(hero.getController(), hero);
                GameEffects.restrictPlayer(botEntity);
                GameEffects.applyEffects(botEntity, botGamePlayer.settings());
            } catch (Exception e) {
                ErrorReporter.report(game, "Failed to initialize bot " + bot.getName() + " (" + botUUID
                        + ") — removing it from the game", e);
                game.removeBot(botUUID);
                NpcServiceProvider.get().remove(bot.getNpcHandle().id());
            }
        }
    }

    void startAbilityCooldowns(Hero hero) {
        if (hero.getSmashAbility() == null) return;
        hero.getSmashAbility().getCooldownObject().start();
        // STOCK smashes start full at construction; drain so ultimates charge from 0 at match start.
        if (hero.getSmashAbility() instanceof BaseAbility base
                && base.getAmmo() != null
                && base.getAmmo().getMode() == Ammo.Mode.STOCK) {
            base.getAmmo().drain();
        }
    }

    void swapHero(GamePlayer gamePlayer, String heroName) {
        Hero oldHero = HeroManager.getHero(gamePlayer.uuid());
        if (oldHero == null) {
            throw new IllegalStateException("Cannot swap hero for participant " + gamePlayer.uuid() + ": no active hero");
        }
        Controller controller = oldHero.getController();

        double ultCharge = 0;
        if (game.modeConfig().ultChargeCarryover() && oldHero.getSmashAbility() != null) {
            Cooldown oldCd = oldHero.getSmashAbility().getCooldownObject();
            long duration = oldCd.getCooldownDuration();
            if (duration > 0) {
                ultCharge = Math.max(0, Math.min(1.0, 1.0 - oldCd.getTimeRemaining() / (double) duration));
            }
        }

        MinionManager.despawnAll(oldHero);
        oldHero.cleanup();

        Hero newHero = HeroManager.createHero(heroName, controller);
        gamePlayer.setCurrentHeroName(heroName);
        if (gamePlayer.isBot()) {
            BotLoadout.configure(gamePlayer.uuid(), gamePlayer.bot(), newHero, heroName);
        }
        CosmeticManager.apply(controller, newHero);
        startAbilityCooldowns(newHero);

        if (ultCharge > 0 && newHero.getSmashAbility() != null) {
            Cooldown newCd = newHero.getSmashAbility().getCooldownObject();
            newCd.reduceCooldown((long) (ultCharge * newCd.getCooldownDuration()));
        }

        if (controller.getEntity() instanceof Player player) {
            if (!gamePlayer.isBot()) {
                PlayerStatsManager.getStats(player).setCurrentContext(game.gameMode(), heroName);
            }
            double hpBefore = player.getHealth();
            GameEffects.applyEffects(player, gamePlayer.settings());
            double max = Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH)).getValue();
            player.setHealth(Math.min(hpBefore, max));
            game.onHealthChanged(player);
        }
    }

    private void initHeroOrder(GamePlayer gamePlayer) {
        List<String> order;
        if (game.modeConfig().sharedEvolutionOrder()) {
            if (sharedEvolutionOrder == null) sharedEvolutionOrder = buildEvolutionOrder();
            order = new ArrayList<>(sharedEvolutionOrder);
        } else {
            order = buildEvolutionOrder();
        }
        int ladder = game.modeConfig().killLimit();
        if (ladder > 0 && ladder < order.size()) order = new ArrayList<>(order.subList(0, ladder));
        gamePlayer.setHeroOrder(order);
        gamePlayer.setHeroIndex(0);
    }

    private List<String> buildEvolutionOrder() {
        List<String> order = new ArrayList<>(HeroConfig.getAvailableNames().stream()
                .filter(name -> !HeroConfig.isAdminOnly(name))
                .toList());
        if (GameModeConfigManager.getEvolutionRandomizeOrder()) {
            Collections.shuffle(order);
        }
        return order;
    }
}
