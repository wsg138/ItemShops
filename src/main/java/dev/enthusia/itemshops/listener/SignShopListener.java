package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.gui.CreateShopMenu;
import dev.enthusia.itemshops.gui.PurchaseMenu;
import dev.enthusia.itemshops.gui.ShopEditMenu;
import dev.enthusia.itemshops.gui.ShopContentsMenu;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;
import dev.enthusia.itemshops.util.Texts;
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

        if (shop != null && p.hasPermission("itemshops.admin")) {
            if (plugin.isAdminInfoActive(p.getUniqueId())) {
                e.setCancelled(true);
                sendShopInfo(p, shop);
                return;
            }
            if (plugin.isAdminViewActive(p.getUniqueId())) {
                e.setCancelled(true);
                new ShopContentsMenu(plugin, p, shop).open();
                return;
            }
        }

        
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (shop != null) {
                e.setCancelled(true);
                boolean isOwner = p.getUniqueId().equals(shop.owner()) || p.hasPermission("itemshops.admin");
                boolean isTrusted = shop.trusted().contains(p.getUniqueId());
                if (isOwner) {
                    new ShopEditMenu(plugin, mgr, p, shop, true, true).open();
                } else if (isTrusted) {
                    new ShopEditMenu(plugin, mgr, p, shop, false, false).open();
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
        if (isOwner && p.isSneaking()) { new ShopEditMenu(plugin, mgr, p, shop, true, true).open(); return; }

        if (plugin.getConfig().getBoolean("sign.show-stock-on-click", true)) {
            Block cont = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
            int trades = mgr.computeTradesAvailable(shop, cont);
            p.sendMessage(Texts.fmt(plugin.messages(),"info.stock-status","trades", trades));
        }
        new PurchaseMenu(plugin, mgr, p, shop).open();
    }

    private void sendShopInfo(Player p, Shop shop) {
        String owner = org.bukkit.Bukkit.getOfflinePlayer(shop.owner()).getName();
        if (owner == null) owner = "Unknown";
        boolean frozen = shop.isFrozen();
        String frozenLine = frozen ? "&cFrozen" : "&aActive";
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&6[ItemShops] &eShop Info"));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Owner: &f" + owner));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Sign: &f" + shop.sign().world + " " + shop.sign().x + "," + shop.sign().y + "," + shop.sign().z));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Container: &f" + shop.container().world + " " + shop.container().x + "," + shop.container().y + "," + shop.container().z));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Trade: &a" + shop.sell().getAmount() + "x " +
                dev.enthusia.itemshops.util.ItemUtils.niceName(shop.sell()) + " &7for &e" + shop.cost().getAmount() + "x " +
                dev.enthusia.itemshops.util.ItemUtils.niceName(shop.cost())));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Trusted: &f" + shop.trusted().size()));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Hoppers: &fIn=" + shop.isHopperAllowIn() + " Out=" + shop.isHopperAllowOut()));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Search: &f" + shop.isSearchEnabled()));
        p.sendMessage(dev.enthusia.itemshops.util.ItemUtils.colored("&7Status: " + frozenLine));
    }
}
