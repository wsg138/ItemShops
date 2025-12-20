package com.lincoln.itemshops.listener;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.gui.ShopEditMenu;
import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.Pos;
import com.lincoln.itemshops.util.Texts;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlockProtectionListener implements Listener {
    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;

    public BlockProtectionListener(ItemShopsPlugin plugin, ShopManager mgr){
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();

        if (b.getState() instanceof Sign) {
            Shop s = (Shop) mgr.get(Pos.of(b.getLocation()));
            if (s != null) {
                if (p.getUniqueId().equals(s.owner()) || p.hasPermission("itemshops.admin")) {
                    e.setCancelled(true);
                    new ShopEditMenu(plugin, mgr, p, s).open();
                    return;
                }
                e.setCancelled(true);
                p.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
            }
            return;
        }

        if (b.getState() instanceof Container) {
            Pos uni = mgr.unifyContainerPos(b);
            Shop s = mgr.byContainer(uni);
            if (s != null) {
                if (!p.getUniqueId().equals(s.owner()) && !p.hasPermission("itemshops.break.others")) {
                    e.setCancelled(true);
                    p.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
                    return;
                }
                mgr.deleteShop(s);
                p.sendMessage(Texts.msg(plugin.messages(), "info.removed"));
            }
        }
    }
}
