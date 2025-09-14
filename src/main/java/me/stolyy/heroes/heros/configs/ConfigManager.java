package me.stolyy.heroes.heros.configs;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final Gson gson = new Gson();
    private final File configFolder;
    private final Map<String, HeroConfig> heroConfigs = new HashMap<>();
    private Map<String, Object> defaultConfigMap;

    public ConfigManager(File configFolder) throws IOException {
        this.configFolder = configFolder;
        if (!configFolder.exists()) {
            if (!configFolder.mkdirs()) {
                throw new IOException("Could not create config directory: " + configFolder.getAbsolutePath());
            }
        }
        loadDefaultConfig();
        loadAllHeroConfigs();
    }

    private void loadDefaultConfig() throws IOException {
        File defaultFile = new File(configFolder, "default.json");
        if (!defaultFile.exists()) {
            throw new IOException("Default config file 'default.json' not found in " + configFolder.getAbsolutePath());
        }
        if (defaultFile.length() == 0) {
            throw new IOException("'default.json' is empty.");
        }

        try (FileReader reader = new FileReader(defaultFile)) {
            Type type = new TypeToken<HashMap<String, Object>>() {}.getType();
            defaultConfigMap = gson.fromJson(reader, type);
            if (defaultConfigMap == null || defaultConfigMap.isEmpty()) {
                throw new IOException("'default.json' contains no valid configuration data.");
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Error parsing 'default.json': " + e.getMessage(), e);
        }
    }

    private void loadAllHeroConfigs() {
        File[] configFiles = configFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json") && !name.equalsIgnoreCase("default.json"));

        if (configFiles == null) {
            System.err.println("[Heroes] Could not list files in config directory. No hero configs will be loaded.");
            return;
        }

        for (File heroFile : configFiles) {
            String fileName = heroFile.getName();
            String heroName = fileName.substring(0, 1).toUpperCase() + fileName.substring(1, fileName.length() - 5);
            try {
                HeroConfig heroConfig = parseHeroConfig(heroName, heroFile);
                heroConfigs.put(heroName, heroConfig);
                System.out.println("[Heroes] Successfully loaded configuration for " + heroName);
            } catch (IOException e) {
                System.err.println("[Heroes] Failed to load configuration for " + heroName + ": " + e.getMessage());
            }
        }
    }

    private HeroConfig parseHeroConfig(String heroName, File heroFile) throws IOException {
        Map<String, Object> finalConfigMap = new HashMap<>(defaultConfigMap);

        if (heroFile.length() == 0) {
            throw new IOException("Hero config file '" + heroFile.getName() + "' is empty.");
        }
        try (FileReader reader = new FileReader(heroFile)) {
            Type type = new TypeToken<HashMap<String, Object>>() {}.getType();
            Map<String, Object> specificConfig = gson.fromJson(reader, type);

            if (specificConfig != null) {
                mergeMaps(finalConfigMap, specificConfig);
            } else {
                throw new IOException("Hero config file '" + heroFile.getName() + "' contains no valid configuration data.");
            }
        } catch (JsonSyntaxException e) {
            throw new IOException("Error parsing '" + heroFile.getName() + "': " + e.getMessage(), e);
        }

        String finalJson = gson.toJson(finalConfigMap);
        try {
            HeroConfig heroConfig = gson.fromJson(finalJson, HeroConfig.class);
            if (heroConfig == null) {
                throw new IOException("Failed to create HeroConfig for " + heroName + " from the combined configuration.");
            }
            return heroConfig;
        } catch (JsonSyntaxException e) {
            throw new IOException("Error creating HeroConfig for " + heroName + ". Check if the JSON structure matches the HeroConfig record. Details: " + e.getMessage(), e);
        }
    }

    public HeroConfig getHeroConfig(String heroName) {
        return heroConfigs.get(heroName);
    }

    @SuppressWarnings("unchecked")
    private void mergeMaps(Map<String, Object> base, Map<String, Object> override) {
        for (String key : override.keySet()) {
            Object overrideValue = override.get(key);
            Object baseValue = base.get(key);

            if (baseValue instanceof Map && overrideValue instanceof Map) {
                mergeMaps((Map<String, Object>) baseValue, (Map<String, Object>) overrideValue);
            } else {
                base.put(key, overrideValue);
            }
        }
    }
}