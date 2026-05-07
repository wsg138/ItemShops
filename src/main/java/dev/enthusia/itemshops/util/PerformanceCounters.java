package dev.enthusia.itemshops.util;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.atomic.LongAdder;

public final class PerformanceCounters {
    public final LongAdder signRefreshQueued = new LongAdder();
    public final LongAdder signRefreshSkipped = new LongAdder();
    public final LongAdder signRefreshProcessed = new LongAdder();
    public final LongAdder signRefreshCoalesced = new LongAdder();
    public final LongAdder storageSaveRequested = new LongAdder();
    public final LongAdder storageSaveWritten = new LongAdder();
    public final LongAdder storageSaveCoalesced = new LongAdder();
    public final LongAdder storageSaveFailed = new LongAdder();
    public final LongAdder auditChecked = new LongAdder();
    public final LongAdder auditRepaired = new LongAdder();
    public final LongAdder auditSkipped = new LongAdder();
    public final LongAdder guiStockCacheHits = new LongAdder();
    public final LongAdder guiStockCacheMisses = new LongAdder();
    public final LongAdder searchIndexHits = new LongAdder();
    public final LongAdder searchIndexMisses = new LongAdder();
    public final LongAdder searchQueries = new LongAdder();
    public final LongAdder recoveryActions = new LongAdder();
    public final LongAdder blockStateSkipped = new LongAdder();
    public final LongAdder nonShopRefreshSkipped = new LongAdder();
    public final LongAdder planCacheHits = new LongAdder();
    public final LongAdder planCacheMisses = new LongAdder();

    private final ItemShopsPlugin plugin;
    private BukkitTask logTask;

    public PerformanceCounters(ItemShopsPlugin plugin) {
        this.plugin = plugin;
    }

    public void restartLogger() {
        stopLogger();
        if (!plugin.pluginConfig().performanceDebugEnabled()) {
            return;
        }
        logTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::logSnapshot,
                plugin.pluginConfig().performanceLogIntervalTicks(),
                plugin.pluginConfig().performanceLogIntervalTicks()
        );
    }

    public void stopLogger() {
        if (logTask != null) {
            logTask.cancel();
            logTask = null;
        }
    }

    public void logSnapshot() {
        plugin.getLogger().info("[Perf] sign queued=" + sum(signRefreshQueued)
                + " skipped=" + sum(signRefreshSkipped)
                + " processed=" + sum(signRefreshProcessed)
                + " coalesced=" + sum(signRefreshCoalesced)
                + " | storage requested=" + sum(storageSaveRequested)
                + " written=" + sum(storageSaveWritten)
                + " coalesced=" + sum(storageSaveCoalesced)
                + " failed=" + sum(storageSaveFailed)
                + " | audit checked=" + sum(auditChecked)
                + " repaired=" + sum(auditRepaired)
                + " skipped=" + sum(auditSkipped)
                + " | gui stock hits=" + sum(guiStockCacheHits)
                + " misses=" + sum(guiStockCacheMisses)
                + " | search hits=" + sum(searchIndexHits)
                + " misses=" + sum(searchIndexMisses)
                + " queries=" + sum(searchQueries)
                + " | recovery=" + sum(recoveryActions)
                + " blockStateSkipped=" + sum(blockStateSkipped)
                + " nonShopRefreshSkipped=" + sum(nonShopRefreshSkipped)
                + " plan hits=" + sum(planCacheHits)
                + " misses=" + sum(planCacheMisses));
    }

    private long sum(LongAdder adder) {
        return adder.sum();
    }
}
