package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;


public final class HopperControlListener implements Listener {
    private final ShopManager mgr;
    public HopperControlListener(ShopManager mgr){ this.mgr = mgr; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        
        Inventory src = e.getSource();
        Inventory dst = e.getDestination();

        
        Pos srcPos = containerPosOf(src);
        if (srcPos != null) {
            List<Shop> shops = mgr.shopsOn(srcPos);
            if (!shops.isEmpty()) {
                boolean allow = shops.stream().allMatch(Shop::isHopperAllowOut);
                if (!allow) { e.setCancelled(true); return; }
                
                mgr.requestSignRefreshForContainer(srcPos);
            }
        }

        
        Pos dstPos = containerPosOf(dst);
        if (dstPos != null) {
            List<Shop> shops = mgr.shopsOn(dstPos);
            if (!shops.isEmpty()) {
                boolean allow = shops.stream().allMatch(Shop::isHopperAllowIn);
                if (!allow) { e.setCancelled(true); return; }
                
                mgr.requestSignRefreshForContainer(dstPos);
            }
        }
    }

    private Pos containerPosOf(Inventory inv) {
        InventoryHolder h = inv.getHolder();
        if (h instanceof Container c) return mgr.unifyContainerPos(c.getBlock());
        if (inv instanceof DoubleChestInventory dci) {
            Container left = (Container) dci.getLeftSide().getHolder();
            return mgr.unifyContainerPos(left.getBlock());
        }
        return null;
    }
}
