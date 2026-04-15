package dev.enthusia.itemshops.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PostShopTransactionEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final UUID landlordId;
    private final String regionId;
    private final String worldName;
    private final ItemStack item;
    private final int quantity;
    private final double pricePaid;

    public PostShopTransactionEvent(Player buyer,
                                    UUID landlordId,
                                    String regionId,
                                    String worldName,
                                    ItemStack item,
                                    int quantity,
                                    double pricePaid) {
        this.buyer = buyer;
        this.landlordId = landlordId;
        this.regionId = regionId;
        this.worldName = worldName;
        this.item = item;
        this.quantity = quantity;
        this.pricePaid = pricePaid;
    }

    public Player getBuyer() {
        return buyer;
    }

    public UUID getLandlordId() {
        return landlordId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getWorldName() {
        return worldName;
    }

    public ItemStack getItem() {
        return item;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPricePaid() {
        return pricePaid;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
