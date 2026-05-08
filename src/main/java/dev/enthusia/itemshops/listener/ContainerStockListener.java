package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;




public final class ContainerStockListener implements Listener {
    private final ShopManager mgr;
    private final Set<Pos> pendingRefreshes = ConcurrentHashMap.newKeySet();

    public ContainerStockListener(ShopManager mgr){ this.mgr = mgr; }

    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!mgr.hasAnyShops()) return;
        Pos p = containerPosOfTop(e.getView());
        queueSettledRefresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!mgr.hasAnyShops()) return;
        Pos p = containerPosOfTop(e.getView());
        queueSettledRefresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent e) {
        if (!mgr.hasAnyShops()) return;
        Pos p = containerPosOfTop(e.getView());
        queueSettledRefresh(p);
    }

    private void queueSettledRefresh(Pos pos) {
        if (pos == null || !mgr.hasShopsOnContainer(pos)) return;
        if (!pendingRefreshes.add(pos)) return;
        ItemShopsPlugin plugin = ItemShopsPlugin.get();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingRefreshes.remove(pos);
            if (!mgr.hasShopsOnContainer(pos)) return;
            mgr.refreshCachedTradesForContainer(pos);
            mgr.requestSignRefreshForContainer(pos);
        }, 1L);
    }

    private Pos containerPosOfTop(InventoryView view) {
        Inventory top = view.getTopInventory();
        InventoryHolder h = top.getHolder();
        if (h instanceof Container c) return mgr.unifyContainerPos(c.getBlock());
        if (top instanceof DoubleChestInventory dci) {
            InventoryHolder leftHolder = dci.getLeftSide() == null ? null : dci.getLeftSide().getHolder();
            InventoryHolder rightHolder = dci.getRightSide() == null ? null : dci.getRightSide().getHolder();
            if (leftHolder instanceof Container left) return mgr.unifyContainerPos(left.getBlock());
            if (rightHolder instanceof Container right) return mgr.unifyContainerPos(right.getBlock());
        }
        return null;
    }
}
