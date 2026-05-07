package dev.enthusia.itemshops.service;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.events.PostShopTransactionEvent;
import dev.enthusia.itemshops.events.PreShopTransactionEvent;
import dev.enthusia.itemshops.events.ShopStockDepletedEvent;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ShopTradeService {
    private final ItemShopsPlugin plugin;
    private final ShopManager shopManager;

    public ShopTradeService(ItemShopsPlugin plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public TradeResult executePurchase(Player buyer, Shop shop, int trades) {
        if (trades <= 0) {
            return TradeResult.failed("errors.buymax-none");
        }
        if (buyer.getUniqueId().equals(shop.owner())) {
            return TradeResult.failed("errors.self-purchase");
        }

        var lock = shop.lock();
        lock.lock();
        try {
            Block contBlock = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
            if (contBlock == null || !(contBlock.getState() instanceof Container cont)) {
                return TradeResult.failed("errors.unsafe");
            }

            long nowMs = System.currentTimeMillis();
            if (shop.clearIfFreezeExpired(nowMs)) {
                shopManager.requestSave();
            }
            if (shop.isFrozen(nowMs)) {
                return TradeResult.failed("errors.shop-frozen");
            }

            Location shopLocation = shop.container().toLocation();
            String regionId = "unknown";
            String worldName = "unknown";
            if (shopLocation != null && shopLocation.getWorld() != null) {
                worldName = shopLocation.getWorld().getName();
                regionId = worldName + ":" + shopLocation.getBlockX() + ":" + shopLocation.getBlockY() + ":" + shopLocation.getBlockZ();
            }

            Inventory contInv = cont.getInventory();
            ItemStack sellT = shop.sell();
            ItemStack costT = shop.cost();
            int sellAmt = sellT.getAmount() * trades;
            int costAmt = costT.getAmount() * trades;
            double originalPrice = computeEventPrice(costT, costAmt, shopLocation);

            PreShopTransactionEvent preEvent = new PreShopTransactionEvent(
                    buyer,
                    regionId,
                    worldName,
                    sellT,
                    trades,
                    originalPrice
            );
            Bukkit.getPluginManager().callEvent(preEvent);
            if (preEvent.isCancelled()) {
                return TradeResult.failed(null);
            }

            int chargedCostAmt = resolveChargedCostAmount(costT, costAmt, preEvent, shopLocation);
            double pricePaid = computeEventPrice(costT, chargedCostAmt, shopLocation);

            int stock = ItemUtils.countSimilar(contInv, sellT);
            if (stock < sellAmt) {
                return TradeResult.failed("errors.stock-empty");
            }

            Inventory buyerInv = buyer.getInventory();
            int have = ItemUtils.countSimilar(buyerInv, costT);
            if (have < chargedCostAmt) {
                return TradeResult.failed("errors.payer-lacks");
            }

            if (!ItemUtils.canFit(buyerInv, sellT, sellAmt)) {
                return TradeResult.failed("errors.buyer-space");
            }

            var removedFromCont = ItemUtils.removeSimilar(contInv, sellT, sellAmt);
            int removedContAmt = removedFromCont.stream().mapToInt(ItemStack::getAmount).sum();
            if (removedContAmt < sellAmt) {
                if (!removedFromCont.isEmpty()) {
                    ItemUtils.rollbackRemove(contInv, sellT, removedFromCont);
                }
                return TradeResult.failed("errors.transaction-failed");
            }

            var removedFromBuyer = ItemUtils.removeSimilar(buyerInv, costT, chargedCostAmt);
            int removedBuyerAmt = removedFromBuyer.stream().mapToInt(ItemStack::getAmount).sum();
            if (removedBuyerAmt < chargedCostAmt) {
                if (!removedFromBuyer.isEmpty()) {
                    ItemUtils.rollbackRemove(buyerInv, costT, removedFromBuyer);
                }
                ItemUtils.rollbackRemove(contInv, sellT, removedFromCont);
                return TradeResult.failed("errors.transaction-failed");
            }

            ItemUtils.addExact(buyerInv, sellT, sellAmt);

            boolean routed = false;
            if (plugin.guildShops() != null && plugin.guildShops().isEnabled()) {
                if (shopLocation != null && plugin.guildShops().isGuildShop(shopLocation) && plugin.guildShops().isPhysicalCurrency(costT)) {
                    int value = plugin.guildShops().calculateCurrencyValue(costT, chargedCostAmt);
                    if (value > 0) {
                        routed = plugin.guildShops().routeShopIncome(shopLocation, (double) value, buyer);
                    }
                }
            }
            if (!routed) {
                plugin.vault().deposit(shop.owner(), costT, chargedCostAmt);
            }

            int remainingStock = ItemUtils.countSimilar(contInv, sellT);
            shopManager.refreshCachedTrades(shop);
            if (remainingStock < sellT.getAmount()) {
                Bukkit.getPluginManager().callEvent(new ShopStockDepletedEvent(shop, shop.owner(), buyer.getUniqueId()));
            }

            Bukkit.getPluginManager().callEvent(new PostShopTransactionEvent(
                    buyer,
                    shop.owner(),
                    regionId,
                    worldName,
                    sellT,
                    trades,
                    pricePaid
            ));

            shopManager.requestSignRefresh(shop);
            return TradeResult.succeeded(sellAmt, chargedCostAmt, sellT, costT);
        } finally {
            lock.unlock();
        }
    }

    private double computeEventPrice(ItemStack costItem, int costAmount, Location shopLocation) {
        if (costAmount <= 0) return 0.0D;
        if (plugin.guildShops() != null
                && plugin.guildShops().isEnabled()
                && shopLocation != null
                && plugin.guildShops().isPhysicalCurrency(costItem)) {
            int value = plugin.guildShops().calculateCurrencyValue(costItem, costAmount);
            if (value > 0) return value;
        }
        return costAmount;
    }

    private int resolveChargedCostAmount(ItemStack costItem, int defaultCostAmount, PreShopTransactionEvent preEvent, Location shopLocation) {
        if (!preEvent.isPriceModified()) {
            return defaultCostAmount;
        }

        double modifiedPrice = preEvent.getModifiedPrice();
        if (modifiedPrice < 0D) {
            return defaultCostAmount;
        }

        if (plugin.guildShops() != null
                && plugin.guildShops().isEnabled()
                && shopLocation != null
                && plugin.guildShops().isPhysicalCurrency(costItem)) {
            int singleItemValue = plugin.guildShops().calculateCurrencyValue(costItem, 1);
            if (singleItemValue <= 0) return defaultCostAmount;

            long roundedPrice = Math.round(modifiedPrice);
            if (Math.abs(modifiedPrice - roundedPrice) > 1.0E-9) return defaultCostAmount;
            if (roundedPrice % singleItemValue != 0L) return defaultCostAmount;

            long convertedAmount = roundedPrice / singleItemValue;
            if (convertedAmount < 0L || convertedAmount > Integer.MAX_VALUE) return defaultCostAmount;
            return (int) convertedAmount;
        }

        long roundedAmount = Math.round(modifiedPrice);
        if (Math.abs(modifiedPrice - roundedAmount) > 1.0E-9) return defaultCostAmount;
        if (roundedAmount < 0L || roundedAmount > Integer.MAX_VALUE) return defaultCostAmount;
        return (int) roundedAmount;
    }

    public static final class TradeResult {
        private final boolean success;
        private final String errorMessageKey;
        private final int sellAmount;
        private final int costAmount;
        private final ItemStack sellItem;
        private final ItemStack costItem;

        private TradeResult(boolean success, String errorMessageKey, int sellAmount, int costAmount, ItemStack sellItem, ItemStack costItem) {
            this.success = success;
            this.errorMessageKey = errorMessageKey;
            this.sellAmount = sellAmount;
            this.costAmount = costAmount;
            this.sellItem = sellItem;
            this.costItem = costItem;
        }

        public static TradeResult failed(String errorMessageKey) {
            return new TradeResult(false, errorMessageKey, 0, 0, null, null);
        }

        public static TradeResult succeeded(int sellAmount, int costAmount, ItemStack sellItem, ItemStack costItem) {
            return new TradeResult(true, null, sellAmount, costAmount, sellItem, costItem);
        }

        public boolean success() {
            return success;
        }

        public String errorMessageKey() {
            return errorMessageKey;
        }

        public int sellAmount() {
            return sellAmount;
        }

        public int costAmount() {
            return costAmount;
        }

        public ItemStack sellItem() {
            return sellItem;
        }

        public ItemStack costItem() {
            return costItem;
        }
    }
}
