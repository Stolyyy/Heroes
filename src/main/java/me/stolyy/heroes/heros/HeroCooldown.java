package me.stolyy.heroes.heros;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.game.minigame.GameManager;
import me.stolyy.heroes.heros.abilities.Ability;
import me.stolyy.heroes.heros.abilities.AbilityType;
import me.stolyy.heroes.utility.Interactions;
import me.stolyy.heroes.utility.effects.Particles;
import me.stolyy.heroes.utility.effects.Sounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

public abstract class HeroCooldown extends Hero {
    private static final Plugin PLUGIN = Heroes.getInstance();
    private static final int BAR_LENGTH = 10;
    private static final int UPDATES_PER_SECOND = 10;

    protected final List<BukkitTask> activeTasks = new LinkedList<>();

    private int doubleJumpCount = 0;
    private boolean canDoubleJump = true;

    public HeroCooldown(Player player){
        super(player);
    }

    public void doubleJump(){
        doubleJumpCount++;
        if(doubleJumpCount >= maxDoubleJumps()) canDoubleJump = false;
        Particles.ring(player.getLocation(), .75, Particle.CLOUD);
        Sounds.playSoundToPlayer(player, Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.0f);
        player.setVelocity(player.getLocation().getDirection().multiply(0.90).setY(0.85));
    }

    public boolean canDoubleJump(){
        return canDoubleJump && doubleJumpCount < maxDoubleJumps();
    }

    public void resetDoubleJumps() {
        doubleJumpCount = 0;
        setMaxDoubleJumps(2);
        canDoubleJump = true;
    }

    public void jab(Player target){
        Interactions.handleStaticInteraction(jabDamage(), 1, player, target);
        onPunch();
    }


    public void onPunch() {
        int reduce = 0;
        switch (heroType()) {
            case MELEE -> reduce = 3;
            case HYBRID -> reduce = 1;
        }
        ultimate.setTimeUntilUse(ultimate.timeUntilUse() - reduce);
    }

    public void onRangedHit() {
        int reduce = 0;
        switch (heroType()) {
            case RANGED -> reduce = 2;
            case HYBRID -> reduce = 1;
        }
        ultimate.setTimeUntilUse(ultimate.timeUntilUse() - reduce);
    }

    public void onCountdown(){
        stats();
    }
    public void onGameStart(){
        if(GameManager.getPlayerGame(player) != null && GameManager.getPlayerGame(player).ultimateEnabled(player)) {
            cooldown(ultimate);
        }
    }
    public void onDeath(){}
    public void onRespawn(){}
    public void onElimination(){
        cancelTasks();
    }

    public void cancelTasks() {
        for (BukkitTask task : activeTasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        activeTasks.clear();
    }

    protected void ultTimer() {
        final Title.Times times = Title.Times.times(
                Duration.ofMillis(250),  // Fade in: 5 ticks
                Duration.ofMillis(1250), // Stay: 25 ticks
                Duration.ofMillis(500)   // Fade out: 10 ticks
        );

        BukkitTask ultTimerTask = new BukkitRunnable() {
            int elapsedTime = 0;

            @Override
            public void run() {
                if (elapsedTime >= ultimate.duration()) {
                    this.cancel();
                    return;
                }

                NamedTextColor color;
                if (elapsedTime < 0.4 * ultimate.duration()) {
                    color = NamedTextColor.DARK_GREEN;
                } else if (elapsedTime < 0.7 * ultimate.duration()) {
                    color = NamedTextColor.YELLOW;
                } else {
                    color = NamedTextColor.RED;
                }

                int timeLeft = (int) (ultimate.duration() - elapsedTime);
                Component mainTitle = Component.text(timeLeft, color);
                Title title = Title.title(mainTitle, Component.empty(), times);
                player.showTitle(title);

                elapsedTime++;
            }
        }.runTaskTimer(PLUGIN, 0L, 20L);
        activeTasks.add(ultTimerTask);
    }

    protected void cooldown(Ability ability) {
        ability.cooldown();
        AbilityType abilityType = ability.abilityType();

        Runnable endLogic = null;
        Runnable updateLogic = null;

        switch (abilityType) {
            case PRIMARY -> {
                endLogic = () -> player.sendActionBar(Component.text("Primary Ability Ready!", NamedTextColor.GREEN));
                updateLogic = () -> {
                    Component prefix = Component.text("Primary Cooldown: ", NamedTextColor.GREEN);
                    Component progressBar = createProgressBar(ability.timeUntilUse() / ability.cd());
                    player.sendActionBar(prefix.append(progressBar));
                };
            }
            case SECONDARY -> {
                endLogic = () -> updateItemDurability(1, (short) 0, 5);
                updateLogic = () -> updateItemDurability(1, ability.timeUntilUse() / ability.cd(), 4);
            }
            case ULTIMATE -> {
                endLogic = () -> {
                    player.sendMessage(Component.text("Ultimate Ability Ready!", NamedTextColor.GREEN));
                    updateItemDurability(2, (short) 0, 7);
                };
                updateLogic = () -> updateItemDurability(2, ability.timeUntilUse() / ability.cd(), 4);
            }
        }

        updateUI(ability, endLogic, updateLogic);
    }

    private void updateUI(Ability ability, Runnable endLogic, Runnable updateLogic) {
        Hero h = this;
        BukkitTask cooldownUITask = new BukkitRunnable() {
            @Override
            public void run() {
                ability.updateCooldown(1.0 / UPDATES_PER_SECOND);
                if(HeroManager.getHero(player) != h){
                    this.cancel();
                    return;
                }

                if (ability.ready()) {
                    if(endLogic != null) endLogic.run();
                    this.cancel();
                }
                else {
                    if(updateLogic != null) updateLogic.run();
                }
            }
        }.runTaskTimer(PLUGIN, 0L, 20L / UPDATES_PER_SECOND);
        activeTasks.add(cooldownUITask);
    }

    protected void updateItemDurability(int slot, double percentComplete, int customModelData) {
        ItemStack item = player.getInventory().getItem(slot);
        percentComplete = Math.min(1, percentComplete);
        ItemMeta meta = item != null ? item.getItemMeta() : null;
        if (meta instanceof Damageable dMeta) {
            dMeta.setCustomModelData(customModelData);
            short maxDurability = item.getType().getMaxDurability();
            short damage = (short) (maxDurability * (percentComplete));
            dMeta.setDamage(damage);
            item.setItemMeta(dMeta);
            player.getInventory().setItem(slot, item);
        }
    }

    private Component createProgressBar(double percentage) {
        percentage = Math.min(1, Math.max(0, percentage));
        int filledBars = (int) (percentage * BAR_LENGTH);

        Component filledPart = Component.text("|".repeat(filledBars), NamedTextColor.GREEN);
        Component emptyPart = Component.text("|".repeat(BAR_LENGTH - filledBars), NamedTextColor.RED);

        return filledPart.append(emptyPart);
    }

    protected int doubleJumpCount() {
        return doubleJumpCount;
    }

    protected void setDoubleJumpCount(int doubleJumpCount){
        this.doubleJumpCount = doubleJumpCount;
    }

    public HeroCooldown setCanDoubleJump(boolean canDoubleJump) {
        this.canDoubleJump = canDoubleJump;
        return this;
    }
}
