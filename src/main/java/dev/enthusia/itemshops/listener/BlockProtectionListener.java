package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.gui.ShopEditMenu;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Pos;
import dev.enthusia.itemshops.util.Texts;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;

public final class BlockProtectionListener implements Listener {
    private static final String ADMIN_PERMISSION = "itemshops.admin";
    private static final String BREAK_OTHERS_PERMISSION = "itemshops.break.others";
    private static final String BREAK_DELETE_PERMISSION = "itemshops.breakdelete";

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;

    public BlockProtectionListener(ItemShopsPlugin plugin, ShopManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!mgr.hasAnyShops()) {
            return;
        }

        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (handleShopSignBreak(event, block, player)) {
            return;
        }

        if (!ItemUtils.couldBeConfiguredContainer(block.getType(), plugin.pluginConfig().allowedContainers())) {
            plugin.performance().blockStateSkipped.increment();
            return;
        }

        if (block.getState() instanceof Container) {
            handleContainerBreak(event, block, player);
        }
    }

    private boolean handleShopSignBreak(BlockBreakEvent event, Block block, Player player) {
        if (!ItemUtils.couldBeSign(block.getType()) || !(block.getState() instanceof Sign)) {
            return false;
        }

        Shop shop = mgr.getBySign(Pos.of(block.getLocation()));
        if (shop == null) {
            return true;
        }

        event.setCancelled(true);
        if (isOwnerOrAdmin(player, shop)) {
            new ShopEditMenu(plugin, mgr, player, shop, true, true).open();
            return true;
        }
        if (shop.trusted().contains(player.getUniqueId())) {
            new ShopEditMenu(plugin, mgr, player, shop, false, false).open();
            return true;
        }
        player.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
        return true;
    }

    private void handleContainerBreak(BlockBreakEvent event, Block block, Player player) {
        Pos containerPos = mgr.unifyContainerPos(block);
        List<Shop> shops = mgr.shopsOn(containerPos);
        if (shops.isEmpty()) {
            return;
        }

        if (isProtectedGuildShopBreak(block, player)) {
            event.setCancelled(true);
            player.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
            return;
        }

        if (shouldBreakDelete(player, shops)) {
            shops.forEach(shop -> mgr.deleteShop(shop, ShopManager.RemovalReason.ADMIN_COMMAND, player.getUniqueId()));
            player.sendMessage(Texts.msg(plugin.messages(), "info.removed"));
            return;
        }

        if (!canBreakContainer(player, shops)) {
            event.setCancelled(true);
            player.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
            return;
        }

        shops.forEach(mgr::deleteShop);
        player.sendMessage(Texts.msg(plugin.messages(), "info.removed"));
    }

    private boolean isProtectedGuildShopBreak(Block block, Player player) {
        return plugin.guildShops() != null
            && plugin.guildShops().isEnabled()
            && plugin.guildShops().isGuildShop(block.getLocation())
            && !player.hasPermission(ADMIN_PERMISSION)
            && !plugin.isBreakOthersActive(player.getUniqueId())
            && !player.hasPermission(BREAK_OTHERS_PERMISSION);
    }

    private boolean shouldBreakDelete(Player player, List<Shop> shops) {
        return plugin.isBreakDeleteActive(player.getUniqueId())
            && player.hasPermission(BREAK_DELETE_PERMISSION)
            && ownsAll(player, shops);
    }

    private boolean canBreakContainer(Player player, List<Shop> shops) {
        return player.hasPermission(BREAK_OTHERS_PERMISSION)
            || plugin.isBreakOthersActive(player.getUniqueId())
            || ownsAll(player, shops);
    }

    private boolean ownsAll(Player player, List<Shop> shops) {
        return shops.stream().allMatch(shop -> shop.owner().equals(player.getUniqueId()));
    }

    private boolean isOwnerOrAdmin(Player player, Shop shop) {
        return player.getUniqueId().equals(shop.owner()) || player.hasPermission(ADMIN_PERMISSION);
    }
}
