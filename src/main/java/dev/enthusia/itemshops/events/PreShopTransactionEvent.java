package dev.enthusia.itemshops.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public class PreShopTransactionEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Player buyer;
    private final String regionId;
    private final String worldName;
    private final ItemStack item;
    private final int quantity;
    private final double originalPrice;

    private double modifiedPrice;
    private String priceModificationReason;
    private boolean priceModified;
    private boolean cancelled;

    public PreShopTransactionEvent(Player buyer,
                                   String regionId,
                                   String worldName,
                                   ItemStack item,
                                   int quantity,
                                   double originalPrice) {
        this.buyer = buyer;
        this.regionId = regionId;
        this.worldName = worldName;
        this.item = item;
        this.quantity = quantity;
        this.originalPrice = originalPrice;
        this.modifiedPrice = originalPrice;
        this.priceModificationReason = null;
        this.priceModified = false;
        this.cancelled = false;
    }

    public Player getBuyer() {
        return buyer;
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

    public double getOriginalPrice() {
        return originalPrice;
    }

    public double getModifiedPrice() {
        return modifiedPrice;
    }

    public void setModifiedPrice(double modifiedPrice, String reason) {
        this.modifiedPrice = modifiedPrice;
        this.priceModificationReason = reason;
        this.priceModified = true;
    }

    public String getPriceModificationReason() {
        return priceModificationReason;
    }

    public boolean isPriceModified() {
        return priceModified;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
