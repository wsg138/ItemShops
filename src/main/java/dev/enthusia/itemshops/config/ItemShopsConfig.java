package dev.enthusia.itemshops.config;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class ItemShopsConfig {
    private final ItemShopsPlugin plugin;

    private int configuredMaxShopsPerPlayer;
    private int configuredMaxShopsPerContainer;
    private boolean configuredOneOwnerPerContainer;
    private int configuredOutsideSpacingRadius;
    private Set<Material> configuredAllowedContainers = Collections.emptySet();
    private int configuredSignUpdateBatchPerRun;
    private long configuredSignUpdateRunDelayTicks;
    private String configuredStorageType;
    private String configuredSqliteFileName;
    private String configuredStorageFileName;
    private boolean configuredDefaultHopperAllowIn;
    private boolean configuredDefaultHopperAllowOut;
    private boolean configuredAnalyticsEnabled;
    private String configuredAnalyticsHistoryFileName;
    private int configuredAnalyticsRetentionDays;
    private int configuredAnalyticsRecentTableLimit;
    private boolean configuredPlanAnalyticsEnabled;
    private boolean configuredShopAuditEnabled;
    private long configuredShopAuditIntervalTicks;
    private int configuredShopAuditMaxShopsPerTick;
    private boolean configuredShopAuditRepairEnabled;
    private boolean configuredShopAuditReportOnly;
    private boolean configuredShopAuditDebugLogRepairs;
    private boolean configuredPerformanceDebugEnabled;
    private long configuredPerformanceLogIntervalTicks;

    public ItemShopsConfig(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        configuredMaxShopsPerPlayer = Math.max(1, plugin.getConfig().getInt("max-shops-per-player", 64));
        configuredMaxShopsPerContainer = Math.max(1, plugin.getConfig().getInt("max-shops-per-container", 2));
        configuredOneOwnerPerContainer = plugin.getConfig().getBoolean("one-owner-per-container", true);
        configuredOutsideSpacingRadius = Math.max(0, plugin.getConfig().getInt("market.outside-spacing-radius", 1));
        configuredStorageType = plugin.getConfig().getString("storage.type", "sqlite").trim().toLowerCase(Locale.ROOT);
        if (!configuredStorageType.equals("sqlite") && !configuredStorageType.equals("yaml")) {
            plugin.getLogger().warning("Unknown storage.type '" + configuredStorageType + "'; using sqlite.");
            configuredStorageType = "sqlite";
        }
        configuredSqliteFileName = plugin.getConfig().getString("storage.sqlite.file", "itemshops.db");
        configuredStorageFileName = plugin.getConfig().getString("storage.file", "shops.yml");
        configuredDefaultHopperAllowIn = plugin.getConfig().getBoolean("hoppers.default-allow-in", false);
        configuredDefaultHopperAllowOut = plugin.getConfig().getBoolean("hoppers.default-allow-out", false);
        configuredAnalyticsEnabled = plugin.getConfig().getBoolean("analytics.enabled", true);
        configuredAnalyticsHistoryFileName = plugin.getConfig().getString("analytics.history-file", "analytics.yml");
        configuredAnalyticsRetentionDays = Math.max(1, plugin.getConfig().getInt("analytics.retention-days", 30));
        configuredAnalyticsRecentTableLimit = Math.max(1, plugin.getConfig().getInt("analytics.recent-table-limit", 10));
        configuredPlanAnalyticsEnabled = plugin.getConfig().getBoolean("analytics.plan.enabled", true);
        configuredShopAuditEnabled = plugin.getConfig().getBoolean("shop-audit.enabled", true);
        configuredShopAuditIntervalTicks = Math.max(20L, plugin.getConfig().getLong("shop-audit.interval-minutes", 10L) * 60L * 20L);
        configuredShopAuditMaxShopsPerTick = Math.max(1, plugin.getConfig().getInt("shop-audit.max-shops-per-tick", 5));
        configuredShopAuditRepairEnabled = plugin.getConfig().getBoolean("shop-audit.repair-enabled", true);
        configuredShopAuditReportOnly = plugin.getConfig().getBoolean("shop-audit.report-only", false);
        configuredShopAuditDebugLogRepairs = plugin.getConfig().getBoolean("shop-audit.debug-log-repairs", false);
        configuredPerformanceDebugEnabled = plugin.getConfig().getBoolean("debug.performance.enabled", false);
        configuredPerformanceLogIntervalTicks = Math.max(20L, plugin.getConfig().getLong("debug.performance.log-interval-seconds", 300L) * 20L);

        // Support both the old broken nested path and the current runtime path.
        configuredSignUpdateBatchPerRun = Math.max(
                1,
                plugin.getConfig().getInt(
                        "sign-update.batch-per-run",
                        plugin.getConfig().getInt("safety.sign-update.max-per-tick", 200)
                )
        );
        configuredSignUpdateRunDelayTicks = Math.max(
                1L,
                plugin.getConfig().getLong(
                        "sign-update.run-delay-ticks",
                        plugin.getConfig().getLong("safety.sign-update.period-ticks", 1L)
                )
        );

        EnumSet<Material> containers = EnumSet.noneOf(Material.class);
        for (String raw : plugin.getConfig().getStringList("allowed-containers")) {
            if (raw == null || raw.isBlank()) continue;
            try {
                containers.add(Material.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        configuredAllowedContainers = Collections.unmodifiableSet(containers);
    }

    public int maxShopsPerPlayer() {
        return configuredMaxShopsPerPlayer;
    }

    public int maxShopsPerContainer() {
        return configuredMaxShopsPerContainer;
    }

    public boolean oneOwnerPerContainer() {
        return configuredOneOwnerPerContainer;
    }

    public int outsideSpacingRadius() {
        return configuredOutsideSpacingRadius;
    }

    public Set<Material> allowedContainers() {
        return configuredAllowedContainers;
    }

    public int signUpdateBatchPerRun() {
        return configuredSignUpdateBatchPerRun;
    }

    public long signUpdateRunDelayTicks() {
        return configuredSignUpdateRunDelayTicks;
    }

    public String storageType() {
        return configuredStorageType;
    }

    public boolean useSqliteStorage() {
        return "sqlite".equals(configuredStorageType);
    }

    public String sqliteFileName() {
        return configuredSqliteFileName;
    }

    public String storageFileName() {
        return configuredStorageFileName;
    }

    public boolean defaultHopperAllowIn() {
        return configuredDefaultHopperAllowIn;
    }

    public boolean defaultHopperAllowOut() {
        return configuredDefaultHopperAllowOut;
    }

    public boolean analyticsEnabled() {
        return configuredAnalyticsEnabled;
    }

    public String analyticsHistoryFileName() {
        return configuredAnalyticsHistoryFileName;
    }

    public int analyticsRetentionDays() {
        return configuredAnalyticsRetentionDays;
    }

    public int analyticsRecentTableLimit() {
        return configuredAnalyticsRecentTableLimit;
    }

    public boolean planAnalyticsEnabled() {
        return configuredPlanAnalyticsEnabled;
    }

    public boolean shopAuditEnabled() {
        return configuredShopAuditEnabled;
    }

    public long shopAuditIntervalTicks() {
        return configuredShopAuditIntervalTicks;
    }

    public int shopAuditMaxShopsPerTick() {
        return configuredShopAuditMaxShopsPerTick;
    }

    public boolean shopAuditRepairEnabled() {
        return configuredShopAuditRepairEnabled;
    }

    public boolean shopAuditReportOnly() {
        return configuredShopAuditReportOnly;
    }

    public boolean shopAuditDebugLogRepairs() {
        return configuredShopAuditDebugLogRepairs;
    }

    public boolean performanceDebugEnabled() {
        return configuredPerformanceDebugEnabled;
    }

    public long performanceLogIntervalTicks() {
        return configuredPerformanceLogIntervalTicks;
    }
}
