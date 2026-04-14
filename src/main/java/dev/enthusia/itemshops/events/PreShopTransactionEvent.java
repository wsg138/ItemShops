package dev.enthusia.itemshops.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * Called before a player purchases an item from a shop.
 * Allows modification of the price or cancellation of the transaction.
 */
public class PreShopTransactionEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    private final Player buyer;
    private final String regionId;
    private final String worldName;
    private final ItemStack item;
    private final int quantity;
    private final double originalPrice;
    private double modifiedPrice;
    private String priceModificationReason;
    private boolean cancelled = false;

    public PreShopTransactionEvent(Player buyer, String regionId, String worldName,
                                    ItemStack item, int quantity, double originalPrice) {
        this.buyer = buyer;
        this.regionId = regionId;
        this.worldName = worldName;
        this.item = item;
        this.quantity = quantity;
        this.originalPrice = originalPrice;
        this.modifiedPrice = originalPrice;
        this.priceModificationReason = null;
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

    /**
     * Set a modified price for this transaction
     * @param newPrice New price to charge
     * @param reason Reason for price modification (shown to player)
     */
    public void setModifiedPrice(double newPrice, String reason) {
        this.modifiedPrice = newPrice;
        this.priceModificationReason = reason;
    }

    public String getPriceModificationReason() {
        return priceModificationReason;
    }

    public boolean isPriceModified() {
        return modifiedPrice != originalPrice;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}
