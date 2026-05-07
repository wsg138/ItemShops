package dev.enthusia.itemshops.analytics;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.events.PostShopTransactionEvent;
import dev.enthusia.itemshops.events.ShopCreatedEvent;
import dev.enthusia.itemshops.events.ShopDeletedEvent;
import dev.enthusia.itemshops.events.ShopStockDepletedEvent;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class ShopAnalyticsListener implements Listener {
    private final ItemShopsPlugin plugin;
    private final ShopAnalyticsStore store;

    public ShopAnalyticsListener(ItemShopsPlugin plugin, ShopAnalyticsStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    @EventHandler
    public void onShopCreated(ShopCreatedEvent event) {
        if (!enabled()) {
            return;
        }
        Shop shop = event.getShop();
        store.record(base(ShopAnalyticsRecord.Type.SHOP_CREATED, shop, event.getOwnerId())
                .soldItem(itemName(shop.sell()))
                .soldAmount(amount(shop.sell()))
                .costItem(itemName(shop.cost()))
                .costAmount(amount(shop.cost()))
                .build());
    }

    @EventHandler
    public void onShopDeleted(ShopDeletedEvent event) {
        if (!enabled()) {
            return;
        }
        Shop shop = event.getShop();
        store.record(base(ShopAnalyticsRecord.Type.SHOP_DELETED, shop, event.getOwnerId())
                .actor(event.getActor(), playerName(event.getActor()))
                .soldItem(itemName(shop.sell()))
                .soldAmount(amount(shop.sell()))
                .costItem(itemName(shop.cost()))
                .costAmount(amount(shop.cost()))
                .reason(event.getReason() == null ? "" : event.getReason().name())
                .build());
    }

    @EventHandler
    public void onPurchase(PostShopTransactionEvent event) {
        if (!enabled()) {
            return;
        }
        Player buyer = event.getBuyer();
        ItemStack item = event.getItem();
        store.record(ShopAnalyticsRecord.builder(ShopAnalyticsRecord.Type.PURCHASE, System.currentTimeMillis())
                .owner(event.getLandlordId(), playerName(event.getLandlordId()))
                .actor(buyer == null ? null : buyer.getUniqueId(), buyer == null ? "" : buyer.getName())
                .worldName(event.getWorldName())
                .location(event.getRegionId())
                .soldItem(itemName(item))
                .soldAmount(event.getQuantity() * amount(item))
                .trades(event.getQuantity())
                .chargedValue(event.getPricePaid())
                .build());
    }

    @EventHandler
    public void onStockDepleted(ShopStockDepletedEvent event) {
        if (!enabled()) {
            return;
        }
        Shop shop = event.getShop();
        store.record(base(ShopAnalyticsRecord.Type.STOCK_DEPLETED, shop, event.getOwnerId())
                .actor(event.getBuyerId(), playerName(event.getBuyerId()))
                .soldItem(itemName(shop.sell()))
                .soldAmount(amount(shop.sell()))
                .build());
    }

    private ShopAnalyticsRecord.Builder base(ShopAnalyticsRecord.Type type, Shop shop, UUID ownerId) {
        return ShopAnalyticsRecord.builder(type, System.currentTimeMillis())
                .shopId(shop == null ? null : shop.id())
                .owner(ownerId, playerName(ownerId))
                .worldName(shop == null || shop.container() == null ? "" : shop.container().world)
                .location(shop == null || shop.container() == null ? "" : shop.container().toString());
    }

    private boolean enabled() {
        return plugin.pluginConfig().analyticsEnabled();
    }

    private String itemName(ItemStack item) {
        return ItemUtils.niceName(item);
    }

    private int amount(ItemStack item) {
        return item == null ? 0 : Math.max(1, item.getAmount());
    }

    private String playerName(UUID playerId) {
        if (playerId == null) {
            return "";
        }
        Player online = plugin.getServer().getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayer(playerId);
        String name = offline.getName();
        return name == null ? playerId.toString() : name;
    }
}
