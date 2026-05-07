package dev.enthusia.itemshops.service;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import org.bukkit.block.Container;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public final class ShopAuditService {
    private final ItemShopsPlugin plugin;
    private final ShopManager shopManager;
    private BukkitTask intervalTask;
    private BukkitTask drainTask;
    private List<Shop> queue = List.of();
    private int index;
    private boolean repairMode;

    public ShopAuditService(ItemShopsPlugin plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public void restart() {
        stop();
        if (!plugin.pluginConfig().shopAuditEnabled()) {
            return;
        }
        intervalTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> startAudit(plugin.pluginConfig().shopAuditRepairEnabled()),
                plugin.pluginConfig().shopAuditIntervalTicks(),
                plugin.pluginConfig().shopAuditIntervalTicks()
        );
    }

    public void stop() {
        if (intervalTask != null) {
            intervalTask.cancel();
            intervalTask = null;
        }
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        queue = List.of();
        index = 0;
    }

    public void startAudit(boolean repairMode) {
        if (drainTask != null) {
            plugin.performance().auditSkipped.increment();
            return;
        }
        this.queue = new ArrayList<>(shopManager.all());
        this.index = 0;
        this.repairMode = repairMode && plugin.pluginConfig().shopAuditRepairEnabled();
        if (queue.isEmpty()) {
            return;
        }
        drainTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drainOnce, 1L, 1L);
    }

    public int runImmediateLimited(int maxShops) {
        List<Shop> shops = new ArrayList<>(shopManager.all());
        int limit = Math.max(1, maxShops);
        int checked = 0;
        for (Shop shop : shops) {
            if (checked >= limit) break;
            auditShop(shop, true);
            checked++;
        }
        return checked;
    }

    private void drainOnce() {
        int max = plugin.pluginConfig().shopAuditMaxShopsPerTick();
        int processed = 0;
        while (processed < max && index < queue.size()) {
            auditShop(queue.get(index++), repairMode);
            processed++;
        }
        if (index >= queue.size()) {
            drainTask.cancel();
            drainTask = null;
            queue = List.of();
            index = 0;
        }
    }

    private void auditShop(Shop shop, boolean repair) {
        if (shop == null) return;
        plugin.performance().auditChecked.increment();
        var location = shop.container().toLocation();
        boolean valid = location != null && location.getBlock().getState() instanceof Container;
        if (!valid) {
            if (repair && !plugin.pluginConfig().shopAuditReportOnly()) {
                shopManager.deleteShop(shop, ShopManager.RemovalReason.INVALID_MISSING_CONTAINER, null);
                plugin.performance().auditRepaired.increment();
                if (plugin.pluginConfig().shopAuditDebugLogRepairs()) {
                    plugin.getLogger().info("[Audit] Removed invalid shop at " + shop.container());
                }
            } else {
                plugin.performance().auditSkipped.increment();
            }
            return;
        }
        shopManager.refreshCachedTrades(shop);
        shopManager.requestSignRefresh(shop);
    }
}
