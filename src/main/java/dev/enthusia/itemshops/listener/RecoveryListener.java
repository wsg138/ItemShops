package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.data.ShopStorage;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.List;

public final class RecoveryListener implements Listener {
    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final ShopStorage storage;

    public RecoveryListener(ItemShopsPlugin plugin, ShopManager mgr, ShopStorage storage) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.storage = storage;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null || !ItemUtils.couldBeSign(clicked.getType())) {
            plugin.performance().blockStateSkipped.increment();
            return;
        }
        if (!(clicked.getState() instanceof Sign sign)) return;

        Pos signPos = Pos.of(clicked.getLocation());
        Shop indexed = mgr.getBySign(signPos);
        if (indexed != null) {
            if (isBlank(sign)) {
                mgr.updateSign(indexed);
            }
            return;
        }

        Block containerBlock = mgr.findAttachedContainer(sign);
        if (containerBlock != null && containerBlock.getState() instanceof Container) {
            Pos containerPos = mgr.unifyContainerPos(containerBlock);
            List<Shop> containerShops = mgr.shopsOn(containerPos);
            if (containerShops.size() == 1) {
                Shop onlyShop = containerShops.get(0);
                if (canSafelyRelink(onlyShop, signPos)) {
                    mgr.relinkSign(onlyShop.sign(), signPos, onlyShop);
                    mgr.updateSign(onlyShop);
                    Player player = e.getPlayer();
                    if (player != null) {
                        player.sendMessage(ItemUtils.colored("&eRepaired shop sign link."));
                    }
                    plugin.performance().recoveryActions.increment();
                    return;
                }
            }
        }

        Shop loaded = storage.loadOneBySign(signPos);
        if (loaded == null) return;

        mgr.put(loaded);
        Player player = e.getPlayer();
        if (player != null) {
            player.sendMessage(ItemUtils.colored("&eRecovered shop from disk."));
        }
        plugin.performance().recoveryActions.increment();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk chunk = e.getChunk();
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof Sign sign)) continue;
            Pos signPos = Pos.of(sign.getLocation());
            Shop shop = mgr.getBySign(signPos);
            if (shop == null || !isBlank(sign)) continue;
            plugin.getServer().getScheduler().runTask(plugin, () -> mgr.updateSign(shop));
        }
    }

    private boolean canSafelyRelink(Shop shop, Pos newSignPos) {
        if (shop == null || newSignPos == null || newSignPos.equals(shop.sign())) return false;
        var existingSignLocation = shop.sign().toLocation();
        if (existingSignLocation == null) return true;
        BlockState state = existingSignLocation.getBlock().getState();
        return !(state instanceof Sign);
    }

    private boolean isBlank(Sign sign) {
        var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        for (int i = 0; i < 4; i++) {
            String line = side.getLine(i);
            if (line != null && !line.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
