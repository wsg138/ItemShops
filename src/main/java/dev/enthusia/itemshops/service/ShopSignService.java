package dev.enthusia.itemshops.service;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopSignService {
    private final ItemShopsPlugin plugin;
    private final Map<UUID, String> ownerNameCache = new ConcurrentHashMap<>();

    public ShopSignService(ItemShopsPlugin plugin) {
        this.plugin = plugin;
    }

    public void clearOwnerCache() {
        ownerNameCache.clear();
    }

    public void clearSign(Shop shop) {
        var location = shop.sign().toLocation();
        if (location == null) return;
        var block = location.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;
        var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            if (!Objects.equals(side.getLine(i), "")) {
                side.setLine(i, "");
                changed = true;
            }
        }
        if (changed) sign.update();
    }

    public void updateSign(Shop shop) {
        var location = shop.sign().toLocation();
        if (location == null) return;

        Block block = location.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        var cfg = plugin.getConfig();
        String headerRaw = cfg.getString("sign.header", "[Shop]");
        String colorIn = cfg.getString("sign.instock-color", "&a");
        String colorOut = cfg.getString("sign.outstock-color", "&c");

        boolean inStock = false;
        var containerLocation = shop.container().toLocation();
        if (containerLocation != null && containerLocation.getBlock().getState() instanceof Container container) {
            inStock = hasAtLeast(container.getInventory(), shop.sell(), shop.sell().getAmount());
        }
        String header = ItemUtils.colored((inStock ? colorIn : colorOut) + headerRaw);

        String ownerName = ownerName(shop.owner());
        String sellName = ItemUtils.niceName(shop.sell());
        String costName = ItemUtils.niceName(shop.cost());

        List<String> lines = cfg.getStringList("sign.format-lines");
        String[] out = new String[4];
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            String line = lines.get(i)
                    .replace("{header}", header)
                    .replace("{owner}", ownerName)
                    .replace("{sell_amt}", String.valueOf(shop.sell().getAmount()))
                    .replace("{sell_name}", sellName)
                    .replace("{cost_amt}", String.valueOf(shop.cost().getAmount()))
                    .replace("{cost_name}", costName);
            out[i] = ItemUtils.colored(line);
        }

        var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            String value = out[i] == null ? "" : out[i];
            if (!Objects.equals(side.getLine(i), value)) {
                side.setLine(i, value);
                changed = true;
            }
        }
        if (changed) sign.update();
    }

    private boolean hasAtLeast(Inventory inv, ItemStack template, int needed) {
        if (inv == null || template == null || needed <= 0) return false;
        int remaining = needed;
        for (ItemStack current : inv.getStorageContents()) {
            if (current == null || current.getType().isAir()) continue;
            if (current.isSimilar(template)) {
                remaining -= current.getAmount();
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    private void cacheOwnerName(UUID ownerId) {
        if (ownerNameCache.containsKey(ownerId)) return;
        Player online = Bukkit.getPlayer(ownerId);
        if (online != null) {
            ownerNameCache.put(ownerId, online.getName());
            return;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(ownerId);
        ownerNameCache.put(ownerId, offline != null && offline.getName() != null ? offline.getName() : "Unknown");
    }

    public String ownerName(UUID ownerId) {
        String name = ownerNameCache.get(ownerId);
        if (name != null) return name;
        cacheOwnerName(ownerId);
        return ownerNameCache.getOrDefault(ownerId, "Unknown");
    }
}
