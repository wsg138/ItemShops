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

import java.util.List;

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
            Container cont = getShopContainer(shop);
            if (cont == null) {
                return TradeResult.failed("errors.unsafe");
            }

            if (isFrozen(shop)) {
                return TradeResult.failed("errors.shop-frozen");
            }

            TradeContext context = buildContext(shop);

            Inventory contInv = cont.getInventory();
            ItemStack sellT = shop.sell();
            ItemStack costT = shop.cost();
            int sellAmt = sellT.getAmount() * trades;
            int costAmt = costT.getAmount() * trades;

            PreShopTransactionEvent preEvent = callPreTransaction(buyer, context, sellT, costT, costAmt, trades);
            if (preEvent.isCancelled()) {
                return TradeResult.failed(null);
            }

            int chargedCostAmt = resolveChargedCostAmount(costT, costAmt, preEvent, context.location());
            TradeResult validationFailure = validateInventories(buyer.getInventory(), contInv, sellT, costT, sellAmt, chargedCostAmt);
            if (validationFailure != null) {
                return validationFailure;
            }

            RemovalResult removal = removeTradeItems(buyer.getInventory(), contInv, sellT, costT, sellAmt, chargedCostAmt);
            if (!removal.success()) {
                return TradeResult.failed("errors.transaction-failed");
            }

            ItemUtils.addExact(buyer.getInventory(), sellT, sellAmt);

            routeIncomeOrDeposit(shop, buyer, context.location(), costT, chargedCostAmt);

            int remainingStock = ItemUtils.countSimilar(contInv, sellT);
            shopManager.refreshCachedTrades(shop);
            if (remainingStock < sellT.getAmount()) {
                Bukkit.getPluginManager().callEvent(new ShopStockDepletedEvent(shop, shop.owner(), buyer.getUniqueId()));
            }

            Bukkit.getPluginManager().callEvent(new PostShopTransactionEvent(
                    buyer,
                    shop.owner(),
                    context.regionId(),
                    context.worldName(),
                    sellT,
                    trades,
                    computeEventPrice(costT, chargedCostAmt, context.location())
            ));

            shopManager.requestSignRefresh(shop);
            return TradeResult.succeeded(sellAmt, chargedCostAmt, sellT, costT);
        } finally {
            lock.unlock();
        }
    }

    private Container getShopContainer(Shop shop) {
        Location containerLocation = shop.container().toLocation();
        Block contBlock = containerLocation == null ? null : containerLocation.getBlock();
        if (contBlock == null || !(contBlock.getState() instanceof Container cont)) {
            return null;
        }
        return cont;
    }

    private boolean isFrozen(Shop shop) {
        long nowMs = System.currentTimeMillis();
        if (shop.clearIfFreezeExpired(nowMs)) {
            shopManager.requestSave();
        }
        return shop.isFrozen(nowMs);
    }

    private TradeContext buildContext(Shop shop) {
        Location shopLocation = shop.container().toLocation();
        if (shopLocation == null || shopLocation.getWorld() == null) {
            return new TradeContext(shopLocation, "unknown", "unknown");
        }
        String worldName = shopLocation.getWorld().getName();
        String regionId = worldName + ":" + shopLocation.getBlockX() + ":" + shopLocation.getBlockY() + ":" + shopLocation.getBlockZ();
        return new TradeContext(shopLocation, regionId, worldName);
    }

    private PreShopTransactionEvent callPreTransaction(
        Player buyer,
        TradeContext context,
        ItemStack sellItem,
        ItemStack costItem,
        int costAmount,
        int trades
    ) {
        PreShopTransactionEvent preEvent = new PreShopTransactionEvent(
            buyer,
            context.regionId(),
            context.worldName(),
            sellItem,
            trades,
            computeEventPrice(costItem, costAmount, context.location())
        );
        Bukkit.getPluginManager().callEvent(preEvent);
        return preEvent;
    }

    private TradeResult validateInventories(Inventory buyerInv, Inventory contInv, ItemStack sellItem, ItemStack costItem, int sellAmount, int chargedCostAmount) {
        if (ItemUtils.countSimilar(contInv, sellItem) < sellAmount) {
            return TradeResult.failed("errors.stock-empty");
        }
        if (ItemUtils.countSimilar(buyerInv, costItem) < chargedCostAmount) {
            return TradeResult.failed("errors.payer-lacks");
        }
        if (!ItemUtils.canFit(buyerInv, sellItem, sellAmount)) {
            return TradeResult.failed("errors.buyer-space");
        }
        return null;
    }

    private RemovalResult removeTradeItems(Inventory buyerInv, Inventory contInv, ItemStack sellItem, ItemStack costItem, int sellAmount, int chargedCostAmount) {
        List<ItemStack> removedFromCont = ItemUtils.removeSimilar(contInv, sellItem, sellAmount);
        if (totalAmount(removedFromCont) < sellAmount) {
            rollbackIfNeeded(contInv, sellItem, removedFromCont);
            return RemovalResult.failed();
        }

        List<ItemStack> removedFromBuyer = ItemUtils.removeSimilar(buyerInv, costItem, chargedCostAmount);
        if (totalAmount(removedFromBuyer) < chargedCostAmount) {
            rollbackIfNeeded(buyerInv, costItem, removedFromBuyer);
            ItemUtils.rollbackRemove(contInv, sellItem, removedFromCont);
            return RemovalResult.failed();
        }
        return RemovalResult.succeeded();
    }

    private int totalAmount(List<ItemStack> items) {
        return items.stream().mapToInt(ItemStack::getAmount).sum();
    }

    private void rollbackIfNeeded(Inventory inventory, ItemStack template, List<ItemStack> removedItems) {
        if (!removedItems.isEmpty()) {
            ItemUtils.rollbackRemove(inventory, template, removedItems);
        }
    }

    private void routeIncomeOrDeposit(Shop shop, Player buyer, Location shopLocation, ItemStack costItem, int chargedCostAmount) {
        if (!routeGuildIncome(buyer, shopLocation, costItem, chargedCostAmount)) {
            plugin.vault().deposit(shop.owner(), costItem, chargedCostAmount);
        }
    }

    private boolean routeGuildIncome(Player buyer, Location shopLocation, ItemStack costItem, int chargedCostAmount) {
        if (plugin.guildShops() == null || !plugin.guildShops().isEnabled() || shopLocation == null) {
            return false;
        }
        if (!plugin.guildShops().isGuildShop(shopLocation) || !plugin.guildShops().isPhysicalCurrency(costItem)) {
            return false;
        }
        int value = plugin.guildShops().calculateCurrencyValue(costItem, chargedCostAmount);
        return value > 0 && plugin.guildShops().routeShopIncome(shopLocation, (double) value, buyer);
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

    private record TradeContext(Location location, String regionId, String worldName) {
    }

    private record RemovalResult(boolean success) {
        private static RemovalResult succeeded() {
            return new RemovalResult(true);
        }

        private static RemovalResult failed() {
            return new RemovalResult(false);
        }
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
