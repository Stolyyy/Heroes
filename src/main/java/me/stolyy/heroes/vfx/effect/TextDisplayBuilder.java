package me.stolyy.heroes.vfx.effect;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.CreatureSpawnEvent;

public class TextDisplayBuilder extends AbstractDisplayBuilder<TextDisplay, TextDisplayBuilder> {

    private Component text;
    private Color backgroundColor;
    private int lineWidth = 200;
    private byte textOpacity = (byte) 255;
    private boolean seeThrough = false;
    private boolean defaultBackground = false;
    private TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;

    private TextDisplayBuilder(Location location) {
        super(location);
    }

    public static TextDisplayBuilder create(Location location) {
        return new TextDisplayBuilder(location);
    }

    public TextDisplayBuilder text(Component text) {
        this.text = text;
        return this;
    }

    public TextDisplayBuilder text(String miniMessage) {
        return text(MiniMessage.miniMessage().deserialize(miniMessage));
    }

    public TextDisplayBuilder backgroundColor(Color color) {
        this.backgroundColor = color;
        this.defaultBackground = false;
        return this;
    }

    public TextDisplayBuilder defaultBackground(boolean defaultBackground) {
        this.defaultBackground = defaultBackground;
        return this;
    }

    public TextDisplayBuilder lineWidth(int width) {
        this.lineWidth = width;
        return this;
    }

    public TextDisplayBuilder opacity(int alpha) {
        this.textOpacity = (byte) alpha;
        return this;
    }
    
    public TextDisplayBuilder seeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
        return this;
    }

    public TextDisplayBuilder alignment(TextDisplay.TextAlignment alignment) {
        this.alignment = alignment;
        return this;
    }

    @Override
    public TextDisplay spawn() {
        if (location.getWorld() == null) return null;
        Location spawnAt = spawnLocation();
        return (TextDisplay) spawnAt.getWorld().spawnEntity(spawnAt, EntityType.TEXT_DISPLAY, CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            TextDisplay display = (TextDisplay) entity;
            applyBase(display);
            if (text != null) {
                display.text(text);
            }
            if (backgroundColor != null) {
                display.setBackgroundColor(backgroundColor);
            }
            display.setLineWidth(lineWidth);
            display.setTextOpacity(textOpacity);
            display.setSeeThrough(seeThrough);
            display.setDefaultBackground(defaultBackground);
            display.setAlignment(alignment);
        });
    }
}
