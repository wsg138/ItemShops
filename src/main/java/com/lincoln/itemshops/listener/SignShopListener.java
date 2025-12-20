package com.lincoln.itemshops.listener;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.gui.CreateShopMenu;
import com.lincoln.itemshops.gui.PurchaseMenu;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;

public final class SignShopListener implements Listener {
    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;

    public SignShopListener(ItemShopsPlugin plugin, ShopManager mgr){
        this.plugin = plugin; this.mgr = mgr;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSignInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !(b.getState() instanceof Sign sign)) return;

        Player p = e.getPlayer();
        Pos signPos = Pos.of(b.getLocation());
        Shop shop = mgr.getBySign(signPos);

        
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (shop != null) {
                e.setCancelled(true);
                if (p.getUniqueId().equals(shop.owner()) || p.hasPermission("itemshops.admin")) {
                    new ShopEditMenu(plugin, mgr, p, shop).open();
                } else {
                    p.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
                }
                return;
            }

            
            if (!p.isSneaking()) return;

            Block container = mgr.findAttachedContainer(sign);
            if (container == null || !(container.getState() instanceof Container) || !mgr.isAllowedContainer(container.getType())) {
                e.setCancelled(true);
                p.sendMessage(Texts.msg(plugin.messages(), "errors.no-container"));
                return;
            }
            if (!mgr.canCreate(p)) {
                e.setCancelled(true);
                p.sendMessage(Texts.fmt(plugin.messages(), "errors.max-shops", "max", plugin.getConfig().getInt("max-shops-per-player")));
                return;
            }
            Pos uni = mgr.unifyContainerPos(container);
            List<Shop> onThis = mgr.shopsOn(uni);
            int limit = plugin.getConfig().getInt("max-shops-per-container", 2);
            if (!onThis.isEmpty()) {
                boolean otherOwner = onThis.stream().anyMatch(s -> !s.owner().equals(p.getUniqueId()));
                if (plugin.getConfig().getBoolean("one-owner-per-container", true) && otherOwner) {
                    e.setCancelled(true);
                    p.sendMessage(Texts.msg(plugin.messages(), "errors.container-claimed"));
                    return;
                }
            }
            if (onThis.size() >= limit) {
                e.setCancelled(true);
                p.sendMessage(Texts.fmt(plugin.messages(), "errors.container-shop-limit", "limit", limit));
                return;
            }

            e.setCancelled(true);
            new CreateShopMenu(plugin, mgr, p, sign, container).open();
            return;
        }

        
        if (shop == null) return;
        e.setCancelled(true);

        boolean isOwner = p.getUniqueId().equals(shop.owner()) || p.hasPermission("itemshops.admin");
        if (isOwner && p.isSneaking()) { new ShopEditMenu(plugin, mgr, p, shop).open(); return; }

        if (plugin.getConfig().getBoolean("sign.show-stock-on-click", true)) {
            Block cont = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
            int trades = mgr.computeTradesAvailable(shop, cont);
            p.sendMessage(Texts.fmt(plugin.messages(),"info.stock-status","trades", trades));
        }
        new PurchaseMenu(plugin, mgr, p, shop).open();
    }
}
