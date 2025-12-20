package com.lincoln.itemshops;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;






public final class YamlLoader {
    private YamlLoader(){}

    
    public static FileConfiguration get(JavaPlugin plugin, String name) {
        mergeDefaults(plugin, name);
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), name));
    }

    
    public static void reload(JavaPlugin plugin, String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) return;
        try {
            mergeDefaults(plugin, name);
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(f); 
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().severe("Failed to reload " + name + ": " + e.getMessage());
        }
    }

    





    public static boolean mergeDefaults(JavaPlugin plugin, String name) {
        plugin.getDataFolder().mkdirs();
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
            return true;
        }

        InputStream raw = plugin.getResource(name);
        if (raw == null) {
            plugin.getLogger().warning("No default " + name + " found in jar; skipping defaults merge.");
            return false;
        }

        boolean changed = false;
        try (InputStreamReader reader = new InputStreamReader(raw, StandardCharsets.UTF_8)) {
            YamlConfiguration current = YamlConfiguration.loadConfiguration(target);
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            for (String path : defaults.getKeys(true)) {
                if (!current.contains(path)) {
                    current.set(path, defaults.get(path));
                    changed = true;
                }
            }
            if (changed) current.save(target);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed merging defaults into " + name + ": " + ex.getMessage());
        }
        return changed;
    }
}
