package dev.enthusia.itemshops.listener;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.data.ShopStorage;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;

public final class RecoveryListener implements Listener {
    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final ShopStorage storage;

    public RecoveryListener(ItemShopsPlugin plugin, ShopManager mgr, ShopStorage storage) {
        this.plugin = plugin; this.mgr = mgr; this.storage = storage;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.LEFT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock(); if (b == null) return;
        if (!(b.getState() instanceof Sign sign)) return;

        Pos signPos = Pos.of(b.getLocation());
        Shop s = mgr.getBySign(signPos);
        if (s != null) {
            
            var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
            boolean blank = true;
            for (int i=0;i<4;i++) { String ln = side.getLine(i); if (ln != null && !ln.isEmpty()) { blank = false; break; } }
            if (blank) mgr.updateSign(s);
            return;
        }

        
        Block contBlock = mgr.findAttachedContainer(sign);
        if (contBlock != null && contBlock.getState() instanceof Container) {
            Pos uni = mgr.unifyContainerPos(contBlock);
            var onCont = mgr.shopsOn(uni);
            if (!onCont.isEmpty()) {
                Shop byCont = onCont.get(0);
                
                mgr.relinkSign(byCont.sign(), signPos, byCont);
                mgr.updateSign(byCont);
                Player p = e.getPlayer();
                if (p != null) p.sendMessage(ItemUtils.colored("&eRepaired shop sign link."));
                return;
            }
        }

        
        Shop loaded = storage.loadOneBySign(signPos);
        if (loaded != null) {
            mgr.put(loaded);
            Player p = e.getPlayer();
            if (p != null) p.sendMessage(ItemUtils.colored("&eRecovered shop from disk."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) {
        Chunk c = e.getChunk();
        String w = c.getWorld().getName();
        int minX = c.getX() << 4, minZ = c.getZ() << 4;
        int maxX = minX + 15, maxZ = minZ + 15;

        for (Shop s : mgr.all()) {
            Pos sp = s.sign();
            if (!sp.world.equals(w)) continue;
            if (sp.x < minX || sp.x > maxX || sp.z < minZ || sp.z > maxZ) continue;

            Block b = sp.toLocation().getBlock();
            if (!(b.getState() instanceof Sign sign)) continue;

            var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
            boolean blank = true;
            for (int i=0;i<4;i++) {
                String line = side.getLine(i);
                if (line != null && !line.isEmpty()) { blank = false; break; }
            }
            if (blank) plugin.getServer().getScheduler().runTask(plugin, () -> mgr.updateSign(s));
        }
    }
}
