package dev.enthusia.itemshops.events;

import dev.enthusia.itemshops.model.Shop;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Called when a shop runs out of stock after a transaction.
 */
public class ShopStockDepletedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final UUID ownerId;
    private final UUID buyerId;

    public ShopStockDepletedEvent(Shop shop, UUID ownerId, UUID buyerId) {
        this.shop = shop;
        this.ownerId = ownerId;
        this.buyerId = buyerId;
    }

    public Shop getShop() { return shop; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getBuyerId() { return buyerId; }

    @Override
    public HandlerList getHandlers() { return handlers; }
    public static HandlerList getHandlerList() { return handlers; }
}
