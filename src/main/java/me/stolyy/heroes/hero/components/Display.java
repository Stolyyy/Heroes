package me.stolyy.heroes.hero.components;

import io.papermc.paper.datacomponent.DataComponentTypes;
import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.characters.Hero;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;

public class Display implements OnTick {
    private final Hero hero;

    private BukkitTask ultimateCountdownTask;
    private final Title.Times times = Title.Times.times(
            Duration.ofMillis(100),  // Fade in
            Duration.ofMillis(1000), // Stay
            Duration.ofMillis(100)   // Fade out
    );

    public Display(Hero hero) {
        this.hero = hero;
    }

    public void ultTitle(double duration){
        if (ultimateCountdownTask != null && !ultimateCountdownTask.isCancelled()) {
            ultimateCountdownTask.cancel();
        }

        this.ultimateCountdownTask = new BukkitRunnable() {
            int secondsLeft = (int) Math.ceil(duration);

            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    this.cancel();
                    return;
                }

                NamedTextColor color;
                if (secondsLeft > duration * 0.7) color = NamedTextColor.GREEN;
                else if (secondsLeft > duration * 0.3) color = NamedTextColor.YELLOW;
                else color = NamedTextColor.RED;

                Component mainTitle = Component.text(secondsLeft, color);
                Title title = Title.title(mainTitle, Component.empty(), times);
                hero.getPlayer().showTitle(title);

                secondsLeft--;
            }
        }.runTaskTimer(Heroes.getInstance(), 0L, 20L);
    }

    public void onTick() {}

    public void clean(){
        if (ultimateCountdownTask != null && !ultimateCountdownTask.isCancelled()) {
            ultimateCountdownTask.cancel();
        }
    }

    public void sendTitle(Component mainTitle, Component subTitle){
        Title title = Title.title(mainTitle, subTitle, times);
        hero.getPlayer().showTitle(title);
    }

    public void sendActionBar(Component text){
        hero.getPlayer().sendActionBar(text);
    }

    public Component progressBar(double percent, int length){
        percent = Math.min(1, Math.max(0, percent));
        int filledBars = (int) (percent * length);

        Component filledPart = Component.text("□".repeat(filledBars), NamedTextColor.GREEN);
        Component emptyPart = Component.text("□".repeat(length - filledBars), NamedTextColor.RED);

        return filledPart.append(emptyPart);
    }

    public void setXPBar(double level, double percent){
        Player player = hero.getPlayer();
        player.setLevel(Math.max((int) level, 0));
        player.setExp(Math.max((float) percent, 0));
    }
}
