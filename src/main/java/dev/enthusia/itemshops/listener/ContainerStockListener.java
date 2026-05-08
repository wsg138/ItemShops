package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ContainerStockListener implements Listener {
    private final ShopManager mgr;
    private final Set<Pos> pendingRefreshes = ConcurrentHashMap.newKeySet();

    public ContainerStockListener(ShopManager mgr) {
        this.mgr = mgr;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!mgr.hasAnyShops()) return;
        Pos pos = containerPosOfTop(event.getView());
        queueSettledRefresh(pos);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!mgr.hasAnyShops()) return;
        Pos pos = containerPosOfTop(event.getView());
        queueSettledRefresh(pos);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        if (!mgr.hasAnyShops()) return;
        Pos pos = containerPosOfTop(event.getView());
        queueSettledRefresh(pos);
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
        InventoryHolder holder = top.getHolder();

        if (holder instanceof Container container) {
            return mgr.unifyContainerPos(container.getBlock());
        }

        if (top instanceof DoubleChestInventory doubleChest) {
            InventoryHolder left = doubleChest.getLeftSide() == null ? null : doubleChest.getLeftSide().getHolder();
            InventoryHolder right = doubleChest.getRightSide() == null ? null : doubleChest.getRightSide().getHolder();

            if (left instanceof Container container) {
                return mgr.unifyContainerPos(container.getBlock());
            }
            if (right instanceof Container container) {
                return mgr.unifyContainerPos(container.getBlock());
            }
        }

        return null;
    }
}
