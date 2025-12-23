package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.gui.ShopEditMenu;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;
import dev.enthusia.itemshops.util.Texts;
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
            Shop s = mgr.getBySign(Pos.of(b.getLocation()));
            if (s != null) {
                boolean isOwner = p.getUniqueId().equals(s.owner()) || p.hasPermission("itemshops.admin");
                boolean isTrusted = s.trusted().contains(p.getUniqueId());
                e.setCancelled(true);
                if (isOwner) {
                    new ShopEditMenu(plugin, mgr, p, s, true, true).open();
                    return;
                }
                if (isTrusted) {
                    new ShopEditMenu(plugin, mgr, p, s, false, false).open();
                    return;
                }
                p.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
            }
            return;
        }

        if (b.getState() instanceof Container) {
            Pos uni = mgr.unifyContainerPos(b);
            var shops = mgr.shopsOn(uni);
            if (!shops.isEmpty()) {
                boolean canBreak = p.hasPermission("itemshops.break.others")
                        || plugin.isBreakOthersActive(p.getUniqueId())
                        || shops.stream().allMatch(s -> s.owner().equals(p.getUniqueId()));
                if (plugin.isBreakDeleteActive(p.getUniqueId()) && p.hasPermission("itemshops.breakdelete")) {
                    if (shops.stream().allMatch(s -> s.owner().equals(p.getUniqueId()))) {
                        for (Shop s : shops) mgr.deleteShop(s, ShopManager.RemovalReason.ADMIN_COMMAND, p.getUniqueId());
                        p.sendMessage(Texts.msg(plugin.messages(), "info.removed"));
                        return;
                    }
                }
                if (!canBreak) {
                    e.setCancelled(true);
                    p.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
                    return;
                }
                for (Shop s : shops) mgr.deleteShop(s);
                p.sendMessage(Texts.msg(plugin.messages(), "info.removed"));
            }
        }
    }
}
