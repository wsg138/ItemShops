package dev.enthusia.itemshops.manager;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.config.ItemShopsConfig;
import dev.enthusia.itemshops.data.ShopStorage;
import dev.enthusia.itemshops.events.ShopCreatedEvent;
import dev.enthusia.itemshops.events.ShopDeletedEvent;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.service.ShopLocator;
import dev.enthusia.itemshops.service.ShopPlacementPolicy;
import dev.enthusia.itemshops.service.ShopRegistry;
import dev.enthusia.itemshops.service.ShopSignService;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public final class ShopManager {

    public enum RemovalReason {
        OWNER_BROKE_CONTAINER, EXPLOSION, INVALID_MISSING_CONTAINER, ADMIN_COMMAND
    }

    private final ItemShopsPlugin plugin;
    private final ShopStorage storage;
    private final ItemShopsConfig config;
    private final ShopRegistry registry;
    private final ShopLocator locator;
    private final ShopPlacementPolicy placementPolicy;
    private final ShopSignService signService;

    private final ConcurrentLinkedQueue<Pos> refreshQueue = new ConcurrentLinkedQueue<>();
    private final java.util.Set<Pos> queued = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean draining = new AtomicBoolean(false);

    private org.bukkit.scheduler.BukkitTask drainTask;

    public ShopManager(ItemShopsPlugin plugin,
                       ShopStorage storage,
                       ItemShopsConfig config,
                       ShopRegistry registry,
                       ShopLocator locator,
                       ShopPlacementPolicy placementPolicy,
                       ShopSignService signService) {
        this.plugin = plugin;
        this.storage = storage;
        this.config = config;
        this.registry = registry;
        this.locator = locator;
        this.placementPolicy = placementPolicy;
        this.signService = signService;
    }

    public void startSignUpdateScheduler() {
        stopSignUpdateScheduler();
    }

    public void stopSignUpdateScheduler() {
        if (drainTask != null) {
            drainTask.cancel();
            drainTask = null;
        }
        draining.set(false);
        refreshQueue.clear();
        queued.clear();
    }

    public void requestSignRefreshForContainer(Pos container) {
        if (container != null && queued.add(container)) {
            refreshQueue.add(container);
            scheduleDrainIfNeeded();
        }
    }

    public void requestSignRefresh(Shop shop) {
        if (shop != null) requestSignRefreshForContainer(shop.container());
    }

    private void scheduleDrainIfNeeded() {
        if (draining.compareAndSet(false, true)) {
            drainTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::drainOnce, config.signUpdateRunDelayTicks());
        }
    }

    private void drainOnce() {
        int processed = 0;
        try {
            while (processed < config.signUpdateBatchPerRun()) {
                Pos pos = refreshQueue.poll();
                if (pos == null) break;
                queued.remove(pos);
                for (Shop shop : registry.shopsOn(pos)) {
                    signService.updateSign(shop);
                }
                processed++;
            }
        } finally {
            if (refreshQueue.isEmpty()) {
                draining.set(false);
                drainTask = null;
                if (!refreshQueue.isEmpty()) {
                    scheduleDrainIfNeeded();
                }
            } else {
                drainTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::drainOnce, config.signUpdateRunDelayTicks());
            }
        }
    }

    public int size() {
        return registry.size();
    }

    public Collection<Shop> all() {
        return registry.all();
    }

    public void clearAll() {
        stopSignUpdateScheduler();
        registry.clear();
        signService.clearOwnerCache();
    }

    public Shop getBySign(Pos signPos) {
        return registry.getBySign(signPos);
    }

    public List<Shop> byContainer(Pos container) {
        return registry.shopsOn(container);
    }

    public List<Shop> shopsOn(Pos pos) {
        return registry.shopsOn(pos);
    }

    public List<Shop> ownedBy(UUID owner) {
        return registry.ownedBy(owner);
    }

    public boolean isAllowedContainer(Material material) {
        return config.allowedContainers().contains(material);
    }

    public boolean canCreate(Player player) {
        return placementPolicy.canCreate(player);
    }

    public void put(Shop shop) {
        registry.put(shop);
        signService.updateSign(shop);
        storage.saveAsync(registry.all());
        Bukkit.getPluginManager().callEvent(new ShopCreatedEvent(shop, shop.owner()));
    }

    public Shop createShop(Sign sign, Block container, Player owner, org.bukkit.inventory.ItemStack sell, org.bukkit.inventory.ItemStack cost) {
        Pos containerPos = locator.unifyContainerPos(container);
        Shop shop = new Shop(Pos.of(sign.getLocation()), containerPos, owner.getUniqueId(), sell.clone(), cost.clone());
        shop.setSearchEnabled(placementPolicy.isInMarket(containerPos));
        put(shop);
        return shop;
    }

    public void relinkSign(Pos oldSign, Pos newSign, Shop shop) {
        registry.relinkSign(oldSign, newSign, shop);
        requestSignRefresh(shop);
        storage.saveAsync(registry.all());
    }

    public void deleteShop(Shop shop) {
        deleteShop(shop, RemovalReason.ADMIN_COMMAND, null);
    }

    public void deleteShop(Shop shop, RemovalReason reason, UUID actor) {
        if (shop == null) return;
        if (!registry.remove(shop)) return;
        signService.clearSign(shop);
        storage.saveAsync(registry.all());
        Bukkit.getPluginManager().callEvent(new ShopDeletedEvent(shop, shop.owner(), reason, actor));
        if (plugin.getConfig().getBoolean("debug.removals", true)) {
            plugin.getLogger().info(String.format(
                    Locale.ROOT,
                    "[ItemShops] Removed shop at %s reason=%s actor=%s",
                    shop.container(),
                    reason,
                    actor
            ));
        }
    }

    public boolean isValid(Shop shop) {
        var location = shop.container().toLocation();
        return location != null && location.getBlock().getState() instanceof Container;
    }

    public int pruneInvalidShops() {
        int removed = 0;
        for (Shop shop : new ArrayList<>(registry.all())) {
            if (!isValid(shop)) {
                deleteShop(shop, RemovalReason.INVALID_MISSING_CONTAINER, null);
                removed++;
            }
        }
        return removed;
    }

    public void clearSign(Shop shop) {
        signService.clearSign(shop);
    }

    public void updateSign(Shop shop) {
        signService.updateSign(shop);
    }

    public boolean isShopSign(Block block) {
        if (block == null) return false;
        return registry.getBySign(Pos.of(block.getLocation())) != null;
    }

    public Block findAttachedContainer(Sign sign) {
        return locator.findAttachedContainer(sign);
    }

    public Pos unifyContainerPos(Block containerBlock) {
        return locator.unifyContainerPos(containerBlock);
    }

    public Inventory getLinkedInventory(Block containerBlock) {
        return locator.getLinkedInventory(containerBlock);
    }

    public List<Shop> search(String query, String mode, int limit) {
        Material mat = ItemUtils.matchMaterial(query);
        return registry.all().stream().filter(shop -> {
            if (!shop.isSearchEnabled()) return false;

            boolean inSell = false;
            boolean inBuy = false;
            if (mat != null) {
                inSell = shop.sell().getType() == mat || ItemUtils.shulkerContains(shop.sell(), mat);
                inBuy = shop.cost().getType() == mat || ItemUtils.shulkerContains(shop.cost(), mat);
            }
            return switch (mode.toLowerCase(Locale.ROOT)) {
                case "sell" -> inSell;
                case "buy" -> inBuy;
                default -> inSell || inBuy;
            };
        }).limit(limit).collect(Collectors.toList());
    }

    public int computeTradesAvailable(Shop shop, Block contBlock) {
        if (shop == null || contBlock == null || !(contBlock.getState() instanceof Container cont)) return 0;
        int stock = ItemUtils.countSimilar(cont.getInventory(), shop.sell());
        return stock / Math.max(1, shop.sell().getAmount());
    }

    public void indexExisting(Shop shop) {
        registry.indexExisting(shop);
    }

    public boolean isInMarket(Pos container) {
        return placementPolicy.isInMarket(container);
    }

    public int outsideSpacingRadius() {
        return placementPolicy.outsideSpacingRadius();
    }

    public Shop findAnyShopWithin(Pos center, int radius) {
        return placementPolicy.findAnyShopWithin(center, radius);
    }

    public String canCreateHere(Pos containerPos) {
        return placementPolicy.validateSpacing(containerPos);
    }

    public String validatePlacement(Player player, Pos containerPos) {
        return placementPolicy.validatePlacement(player, containerPos);
    }

    public void requestSave() {
        storage.saveAsync(registry.all());
    }

    public void setTrusted(Shop shop, UUID target, boolean trusted) {
        if (trusted) {
            shop.addTrusted(target);
        } else {
            shop.removeTrusted(target);
        }
        requestSave();
    }

    public void setTrusted(Collection<Shop> shops, UUID target, boolean trusted) {
        if (shops == null || shops.isEmpty()) return;
        for (Shop shop : shops) {
            if (trusted) {
                shop.addTrusted(target);
            } else {
                shop.removeTrusted(target);
            }
        }
        requestSave();
    }

    public void setHopperAllowIn(Shop shop, boolean enabled) {
        shop.setHopperAllowIn(enabled);
        requestSave();
    }

    public void setHopperAllowOut(Shop shop, boolean enabled) {
        shop.setHopperAllowOut(enabled);
        requestSave();
    }

    public void setSearchEnabled(Shop shop, boolean enabled) {
        shop.setSearchEnabled(enabled);
        requestSave();
    }

    public void updateTradeItems(Shop shop, org.bukkit.inventory.ItemStack sell, org.bukkit.inventory.ItemStack cost) {
        shop.setSell(sell.clone());
        shop.setCost(cost.clone());
        signService.updateSign(shop);
        requestSave();
    }

    public void freezeShop(Shop shop, long untilMs) {
        if (untilMs > 0L) {
            shop.freezeUntil(untilMs);
        } else {
            shop.freezeIndefinitely();
        }
        requestSave();
    }

    public void unfreezeShop(Shop shop) {
        shop.unfreeze();
        requestSave();
    }

    public void freezeShops(Collection<Shop> shops, long untilMs) {
        if (shops == null || shops.isEmpty()) return;
        for (Shop shop : shops) {
            if (untilMs > 0L) {
                shop.freezeUntil(untilMs);
            } else {
                shop.freezeIndefinitely();
            }
        }
        requestSave();
    }

    public void unfreezeShops(Collection<Shop> shops) {
        if (shops == null || shops.isEmpty()) return;
        for (Shop shop : shops) {
            shop.unfreeze();
        }
        requestSave();
    }

    public void loadFromStorage() {
        clearAll();
        for (Shop shop : storage.loadAll()) {
            registry.indexExisting(shop);
        }
    }

    public void saveNow() {
        storage.saveAll(registry.all());
    }

    public void shutdown() {
        stopSignUpdateScheduler();
        storage.shutdownAndSave(registry.all());
    }
}
