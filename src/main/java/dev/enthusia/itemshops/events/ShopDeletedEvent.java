package dev.enthusia.itemshops.events;

import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Called after a shop has been deleted/removed.
 */
public class ShopDeletedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final UUID ownerId;
    private final ShopManager.RemovalReason reason;
    private final UUID actor; // nullable — null for system removals

    public ShopDeletedEvent(Shop shop, UUID ownerId, ShopManager.RemovalReason reason, UUID actor) {
        this.shop = shop;
        this.ownerId = ownerId;
        this.reason = reason;
        this.actor = actor;
    }

    public Shop getShop() {
        return shop;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public ShopManager.RemovalReason getReason() {
        return reason;
    }

    public UUID getActor() {
        return actor;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
