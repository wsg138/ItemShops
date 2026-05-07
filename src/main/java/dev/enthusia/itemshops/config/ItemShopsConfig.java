package dev.enthusia.itemshops.config;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.Material;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class ItemShopsConfig {
    private final ItemShopsPlugin plugin;

    private int maxShopsPerPlayer;
    private int maxShopsPerContainer;
    private boolean oneOwnerPerContainer;
    private int outsideSpacingRadius;
    private Set<Material> allowedContainers = Collections.emptySet();
    private int signUpdateBatchPerRun;
    private long signUpdateRunDelayTicks;
    private String storageFileName;
    private boolean defaultHopperAllowIn;
    private boolean defaultHopperAllowOut;
    private boolean analyticsEnabled;
    private String analyticsHistoryFileName;
    private int analyticsRetentionDays;
    private int analyticsRecentTableLimit;
    private boolean planAnalyticsEnabled;
    private boolean shopAuditEnabled;
    private long shopAuditIntervalTicks;
    private int shopAuditMaxShopsPerTick;
    private boolean shopAuditRepairEnabled;
    private boolean shopAuditReportOnly;
    private boolean shopAuditDebugLogRepairs;
    private boolean performanceDebugEnabled;
    private long performanceLogIntervalTicks;

    public ItemShopsConfig(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        maxShopsPerPlayer = Math.max(1, plugin.getConfig().getInt("max-shops-per-player", 64));
        maxShopsPerContainer = Math.max(1, plugin.getConfig().getInt("max-shops-per-container", 2));
        oneOwnerPerContainer = plugin.getConfig().getBoolean("one-owner-per-container", true);
        outsideSpacingRadius = Math.max(0, plugin.getConfig().getInt("market.outside-spacing-radius", 1));
        storageFileName = plugin.getConfig().getString("storage.file", "shops.yml");
        defaultHopperAllowIn = plugin.getConfig().getBoolean("hoppers.default-allow-in", false);
        defaultHopperAllowOut = plugin.getConfig().getBoolean("hoppers.default-allow-out", false);
        analyticsEnabled = plugin.getConfig().getBoolean("analytics.enabled", true);
        analyticsHistoryFileName = plugin.getConfig().getString("analytics.history-file", "analytics.yml");
        analyticsRetentionDays = Math.max(1, plugin.getConfig().getInt("analytics.retention-days", 30));
        analyticsRecentTableLimit = Math.max(1, plugin.getConfig().getInt("analytics.recent-table-limit", 10));
        planAnalyticsEnabled = plugin.getConfig().getBoolean("analytics.plan.enabled", true);
        shopAuditEnabled = plugin.getConfig().getBoolean("shop-audit.enabled", true);
        shopAuditIntervalTicks = Math.max(20L, plugin.getConfig().getLong("shop-audit.interval-minutes", 10L) * 60L * 20L);
        shopAuditMaxShopsPerTick = Math.max(1, plugin.getConfig().getInt("shop-audit.max-shops-per-tick", 5));
        shopAuditRepairEnabled = plugin.getConfig().getBoolean("shop-audit.repair-enabled", true);
        shopAuditReportOnly = plugin.getConfig().getBoolean("shop-audit.report-only", false);
        shopAuditDebugLogRepairs = plugin.getConfig().getBoolean("shop-audit.debug-log-repairs", false);
        performanceDebugEnabled = plugin.getConfig().getBoolean("debug.performance.enabled", false);
        performanceLogIntervalTicks = Math.max(20L, plugin.getConfig().getLong("debug.performance.log-interval-seconds", 300L) * 20L);

        // Support both the old broken nested path and the current runtime path.
        signUpdateBatchPerRun = Math.max(
                1,
                plugin.getConfig().getInt(
                        "sign-update.batch-per-run",
                        plugin.getConfig().getInt("safety.sign-update.max-per-tick", 200)
                )
        );
        signUpdateRunDelayTicks = Math.max(
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
        allowedContainers = Collections.unmodifiableSet(containers);
    }

    public int maxShopsPerPlayer() {
        return maxShopsPerPlayer;
    }

    public int maxShopsPerContainer() {
        return maxShopsPerContainer;
    }

    public boolean oneOwnerPerContainer() {
        return oneOwnerPerContainer;
    }

    public int outsideSpacingRadius() {
        return outsideSpacingRadius;
    }

    public Set<Material> allowedContainers() {
        return allowedContainers;
    }

    public int signUpdateBatchPerRun() {
        return signUpdateBatchPerRun;
    }

    public long signUpdateRunDelayTicks() {
        return signUpdateRunDelayTicks;
    }

    public String storageFileName() {
        return storageFileName;
    }

    public boolean defaultHopperAllowIn() {
        return defaultHopperAllowIn;
    }

    public boolean defaultHopperAllowOut() {
        return defaultHopperAllowOut;
    }

    public boolean analyticsEnabled() {
        return analyticsEnabled;
    }

    public String analyticsHistoryFileName() {
        return analyticsHistoryFileName;
    }

    public int analyticsRetentionDays() {
        return analyticsRetentionDays;
    }

    public int analyticsRecentTableLimit() {
        return analyticsRecentTableLimit;
    }

    public boolean planAnalyticsEnabled() {
        return planAnalyticsEnabled;
    }

    public boolean shopAuditEnabled() {
        return shopAuditEnabled;
    }

    public long shopAuditIntervalTicks() {
        return shopAuditIntervalTicks;
    }

    public int shopAuditMaxShopsPerTick() {
        return shopAuditMaxShopsPerTick;
    }

    public boolean shopAuditRepairEnabled() {
        return shopAuditRepairEnabled;
    }

    public boolean shopAuditReportOnly() {
        return shopAuditReportOnly;
    }

    public boolean shopAuditDebugLogRepairs() {
        return shopAuditDebugLogRepairs;
    }

    public boolean performanceDebugEnabled() {
        return performanceDebugEnabled;
    }

    public long performanceLogIntervalTicks() {
        return performanceLogIntervalTicks;
    }
}
