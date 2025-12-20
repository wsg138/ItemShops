package com.lincoln.itemshops.listener;

import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.util.Pos;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;





public final class ContainerStockListener implements Listener {
    private final ShopManager mgr;
    public ContainerStockListener(ShopManager mgr){ this.mgr = mgr; }

    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        Pos p = containerPosOfTop(e.getView());
        if (p != null) mgr.requestSignRefreshForContainer(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        Pos p = containerPosOfTop(e.getView());
        if (p != null) mgr.requestSignRefreshForContainer(p);
    }

    private Pos containerPosOfTop(InventoryView view) {
        Inventory top = view.getTopInventory();
        InventoryHolder h = top.getHolder();
        if (h instanceof Container c) return mgr.unifyContainerPos(c.getBlock());
        if (top instanceof DoubleChestInventory dci) {
            Container left = (Container) dci.getLeftSide().getHolder();
            return mgr.unifyContainerPos(left.getBlock());
        }
        return null;
    }
}
