package com.lincoln.itemshops.listener;

import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.Pos;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayList;
import java.util.List;

public final class ExplodeCleanupListener implements Listener {
    private final ShopManager mgr;
    public ExplodeCleanupListener(ShopManager mgr){ this.mgr = mgr; }

    @EventHandler public void onEntityExplode(EntityExplodeEvent e) { handle(e.blockList()); }
    @EventHandler public void onBlockExplode(BlockExplodeEvent e) { handle(e.blockList()); }

    private void handle(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) return;
        List<Shop> toRemove = new ArrayList<>();
        for (Block b : blocks) {
            if (b.getState() instanceof Sign) {
                var s = mgr.get(Pos.of(b.getLocation()));
                if (s != null && !toRemove.contains(s)) toRemove.add((Shop) s);
            }
            if (b.getState() instanceof Container) {
                var uni = mgr.unifyContainerPos(b);
                for (Shop s : mgr.shopsOn(uni)) if (!toRemove.contains(s)) toRemove.add(s);
            }
        }
        for (Shop s : toRemove) mgr.deleteShop(s);
    }
}
