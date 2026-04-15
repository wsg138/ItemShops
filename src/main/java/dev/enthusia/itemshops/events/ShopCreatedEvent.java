package dev.enthusia.itemshops.events;

import dev.enthusia.itemshops.model.Shop;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class ShopCreatedEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    private final Shop shop;
    private final UUID ownerId;

    public ShopCreatedEvent(Shop shop, UUID ownerId) {
        this.shop = shop;
        this.ownerId = ownerId;
    }

    public Shop getShop() {
        return shop;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
