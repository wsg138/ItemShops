package dev.enthusia.itemshops.websync;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class WebsiteSyncSettings {
    private final JavaPlugin plugin;
    private final File file;
    private volatile Values values;

    public WebsiteSyncSettings(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "website-sync.yml");
        reload();
    }

    public synchronized void reload() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        values = read(yaml);
    }

    public Values values() { return values; }
    public boolean secretConfigured() { return !values.secret().isEmpty(); }

    public synchronized void setSecret(String secret) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("sync-secret", secret == null ? "" : secret);
        atomicSave(yaml);
        values = read(yaml);
    }

    public synchronized void setEnabled(boolean enabled) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("enabled", enabled);
        atomicSave(yaml);
        values = read(yaml);
    }

    private Values read(YamlConfiguration yaml) {
        return new Values(
                yaml.getBoolean("enabled", true),
                yaml.getString("endpoint", "https://market-api.enthusia.info"),
                yaml.getString("server-id", "enthusia-main"),
                yaml.getString("sync-secret", ""),
                bounded(yaml.getInt("timing.startup-delay-seconds", 30), 0, 3600),
                bounded(yaml.getInt("timing.stall-debounce-milliseconds", 250), 50, 60_000),
                bounded(yaml.getInt("timing.reconciliation-minutes", 15), 1, 1440),
                bounded(yaml.getInt("http.connect-timeout-seconds", 10), 1, 120),
                bounded(yaml.getInt("http.request-timeout-seconds", 15), 1, 300),
                bounded(yaml.getInt("http.maximum-concurrent-requests", 2), 1, 8),
                bounded(yaml.getInt("retry.initial-delay-seconds", 5), 1, 3600),
                bounded(yaml.getInt("retry.maximum-delay-seconds", 300), 1, 86_400),
                bounded(yaml.getInt("retry.maximum-attempts-before-cooldown", 10), 1, 100),
                yaml.getString("outbox.database-file", "website-sync-outbox.db"),
                yaml.getBoolean("logging.status-changes", true),
                yaml.getBoolean("logging.successful-stall-updates", false));
    }

    private static int bounded(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void atomicSave(YamlConfiguration yaml) throws IOException {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(temporary.toPath(), yaml.saveToString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Values(boolean enabled, String endpoint, String serverId, String secret,
                         int startupDelaySeconds, int stallDebounceMilliseconds, int reconciliationMinutes,
                         int connectTimeoutSeconds, int requestTimeoutSeconds, int maximumConcurrentRequests,
                         int initialRetrySeconds, int maximumRetrySeconds, int maximumAttemptsBeforeCooldown,
                         String outboxDatabaseFile, boolean logStatusChanges, boolean logSuccessfulStallUpdates) {}
}
