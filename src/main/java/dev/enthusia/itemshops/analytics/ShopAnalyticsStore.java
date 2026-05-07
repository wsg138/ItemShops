package dev.enthusia.itemshops.analytics;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ShopAnalyticsStore {
    private static final long SAVE_DEBOUNCE_TICKS = 100L;

    private final ItemShopsPlugin plugin;
    private final List<ShopAnalyticsRecord> records = new ArrayList<>();
    private final EnumMap<ShopAnalyticsRecord.Type, Long> totalByType = new EnumMap<>(ShopAnalyticsRecord.Type.class);
    private File file;
    private BukkitTask pendingSaveTask;

    public ShopAnalyticsStore(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        synchronized (records) {
            records.clear();
            totalByType.clear();
        }
        file = new File(plugin.getDataFolder(), plugin.pluginConfig().analyticsHistoryFileName());
        ensureFile();
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("records");
        if (root == null) {
            return;
        }
        List<ShopAnalyticsRecord> loaded = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            ShopAnalyticsRecord record = ShopAnalyticsRecord.deserialize(root.getConfigurationSection(key));
            if (record != null) {
                loaded.add(record);
            }
        }
        loaded.sort(Comparator.comparingLong(ShopAnalyticsRecord::timestampMs));
        synchronized (records) {
            records.addAll(loaded);
            pruneExpired(System.currentTimeMillis());
            rebuildTotals();
        }
    }

    public void record(ShopAnalyticsRecord record) {
        if (record == null || !plugin.pluginConfig().analyticsEnabled()) {
            return;
        }
        synchronized (records) {
            records.add(record);
            pruneExpired(System.currentTimeMillis());
            totalByType.merge(record.type(), 1L, Long::sum);
        }
        saveAsync();
    }

    public long count(ShopAnalyticsRecord.Type type, Duration window) {
        long since = since(window);
        synchronized (records) {
            return records.stream()
                    .filter(record -> record.type() == type)
                    .filter(record -> record.timestampMs() >= since)
                    .count();
        }
    }

    public long countForPlayer(UUID playerId, ShopAnalyticsRecord.Type type, Duration window, Predicate<ShopAnalyticsRecord> filter) {
        if (playerId == null) {
            return 0L;
        }
        long since = since(window);
        Predicate<ShopAnalyticsRecord> effectiveFilter = filter == null ? record -> true : filter;
        synchronized (records) {
            return records.stream()
                    .filter(record -> record.type() == type)
                    .filter(record -> record.timestampMs() >= since)
                    .filter(record -> record.involves(playerId))
                    .filter(effectiveFilter)
                    .count();
        }
    }

    public ShopAnalyticsRecord latestForPlayer(UUID playerId, ShopAnalyticsRecord.Type type, Predicate<ShopAnalyticsRecord> filter) {
        if (playerId == null) {
            return null;
        }
        Predicate<ShopAnalyticsRecord> effectiveFilter = filter == null ? record -> true : filter;
        synchronized (records) {
            return records.stream()
                    .filter(record -> record.type() == type)
                    .filter(record -> record.involves(playerId))
                    .filter(effectiveFilter)
                    .max(Comparator.comparingLong(ShopAnalyticsRecord::timestampMs))
                    .orElse(null);
        }
    }

    public List<ShopAnalyticsRecord> recent(ShopAnalyticsRecord.Type type, int limit) {
        return recent(record -> record.type() == type, limit);
    }

    public List<ShopAnalyticsRecord> recentForPlayer(UUID playerId, ShopAnalyticsRecord.Type type, int limit, Predicate<ShopAnalyticsRecord> filter) {
        if (playerId == null) {
            return List.of();
        }
        Predicate<ShopAnalyticsRecord> effectiveFilter = filter == null ? record -> true : filter;
        return recent(record -> record.type() == type && record.involves(playerId) && effectiveFilter.test(record), limit);
    }

    public void saveNow() {
        BukkitTask task = pendingSaveTask;
        if (task != null) {
            task.cancel();
            pendingSaveTask = null;
        }
        saveSnapshot(snapshot());
    }

    public void shutdown() {
        saveNow();
    }

    public List<ShopAnalyticsRecord> recent(Predicate<ShopAnalyticsRecord> filter, int limit) {
        int effectiveLimit = Math.max(1, limit);
        synchronized (records) {
            return records.stream()
                    .filter(filter)
                    .sorted(Comparator.comparingLong(ShopAnalyticsRecord::timestampMs).reversed())
                    .limit(effectiveLimit)
                    .toList();
        }
    }

    private long since(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            return 0L;
        }
        return System.currentTimeMillis() - window.toMillis();
    }

    private void pruneExpired(long nowMs) {
        long retentionMs = TimeUnit.DAYS.toMillis(plugin.pluginConfig().analyticsRetentionDays());
        long cutoff = nowMs - retentionMs;
        if (records.removeIf(record -> record.timestampMs() < cutoff)) {
            rebuildTotals();
        }
    }

    private void rebuildTotals() {
        totalByType.clear();
        for (ShopAnalyticsRecord record : records) {
            totalByType.merge(record.type(), 1L, Long::sum);
        }
    }

    private List<ShopAnalyticsRecord> snapshot() {
        synchronized (records) {
            pruneExpired(System.currentTimeMillis());
            return new ArrayList<>(records);
        }
    }

    private void saveAsync() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
        }
        pendingSaveTask = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            pendingSaveTask = null;
            saveSnapshot(snapshot());
        }, SAVE_DEBOUNCE_TICKS);
    }

    private void saveSnapshot(List<ShopAnalyticsRecord> snapshot) {
        FileConfiguration out = new YamlConfiguration();
        for (int i = 0; i < snapshot.size(); i++) {
            out.set("records." + i, snapshot.get(i).serialize());
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            out.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed saving ItemShops analytics history: " + e.getMessage());
        } finally {
            if (tmp.exists() && !tmp.equals(file)) {
                tmp.delete();
            }
        }
    }

    private void ensureFile() {
        if (file.exists()) {
            return;
        }
        try {
            plugin.getDataFolder().mkdirs();
            if (!file.createNewFile()) {
                plugin.getLogger().warning("Could not create analytics history file " + file.getName());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed creating analytics history file: " + e.getMessage());
        }
    }
}
