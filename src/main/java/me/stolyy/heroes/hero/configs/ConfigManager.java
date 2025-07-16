package me.stolyy.heroes.hero.configs;

import me.stolyy.heroes.Heroes;
import me.stolyy.heroes.hero.characters.Hero;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final File CONFIG_FILE = new File(Heroes.getInstance().getDataFolder(), "/heroes");
    private static Map<Class<Hero>, HeroConfig> heroConfigs = new HashMap<>();

    public static void loadConfigs() {
        // Logic to load hero configurations from a file or database
    }

    public static HeroConfig getHeroConfig(Class<Hero> heroClass) {
        if (heroConfigs.containsKey(heroClass)) {
            return heroConfigs.get(heroClass);
        } else {
            throw new IllegalArgumentException("No configuration found for hero class: " + heroClass.getName());
        }
    }

    private static HeroConfig parseConfigFromJson(File jsonFile){
        return new HeroConfig();
    }
}
