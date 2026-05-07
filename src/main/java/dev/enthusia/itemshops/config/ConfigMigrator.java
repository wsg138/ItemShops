package dev.enthusia.itemshops.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ConfigMigrator {
    public static final int CONFIG_VERSION = 2;
    public static final int MESSAGES_VERSION = 1;

    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JavaPlugin plugin;

    public ConfigMigrator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void migrateStartupFiles() {
        migrateYaml("config.yml", CONFIG_VERSION, "config-version");
        migrateYaml("messages.yml", MESSAGES_VERSION, "messages-version");
    }

    private void migrateYaml(String name, int currentVersion, String versionPath) {
        File target = new File(plugin.getDataFolder(), name);
        if (!target.exists()) {
            plugin.saveResource(name, false);
            plugin.getLogger().info("Created default " + name + ".");
            return;
        }

        var raw = plugin.getResource(name);
        if (raw == null) {
            plugin.getLogger().warning("No default " + name + " found in jar; skipping migration.");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(raw, StandardCharsets.UTF_8)) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            YamlConfiguration current = YamlConfiguration.loadConfiguration(target);
            int existingVersion = current.getInt(versionPath, 0);
            List<String> added = new ArrayList<>();

            for (String path : defaults.getKeys(true)) {
                if (!current.contains(path)) {
                    current.set(path, defaults.get(path));
                    added.add(path);
                }
            }

            boolean versionChanged = existingVersion < currentVersion;
            if (!added.isEmpty() || versionChanged) {
                backup(target);
                current.set(versionPath, currentVersion);
                current.save(target);
                if (!added.isEmpty()) {
                    plugin.getLogger().info(name + " migration added missing keys: " + String.join(", ", added));
                }
                if (versionChanged) {
                    plugin.getLogger().info(name + " migrated from version " + existingVersion + " to " + currentVersion + ".");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not safely migrate " + name + ": " + e.getMessage());
            plugin.getLogger().warning("Keeping existing " + name + ". Check the backup file if one was created.");
        }
    }

    private void backup(File target) throws IOException {
        File backupDir = new File(plugin.getDataFolder(), "backups");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        String stamp = LocalDateTime.now().format(BACKUP_STAMP);
        File backup = new File(backupDir, target.getName() + "." + stamp + ".bak");
        Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        plugin.getLogger().info("Backed up " + target.getName() + " to " + backup.getName() + ".");
    }
}
