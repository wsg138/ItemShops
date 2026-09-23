package dev.enthusia.itemshops.events;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopTransactionEventTest {
    private static final UUID LANDLORD_ID = UUID.fromString("10000000-0000-0000-0000-000000000010");

    @Test
    void preTransactionStartsUnmodifiedAndUncancelled() {
        ItemStack item = new ItemStack(Material.DIAMOND, 2);
        PreShopTransactionEvent event = new PreShopTransactionEvent(
                null,
                "spawn-shop",
                "world",
                item,
                4,
                25.0D
        );

        assertNull(event.getBuyer());
        assertEquals("spawn-shop", event.getRegionId());
        assertEquals("world", event.getWorldName());
        assertSame(item, event.getItem());
        assertEquals(4, event.getQuantity());
        assertEquals(25.0D, event.getOriginalPrice());
        assertEquals(25.0D, event.getModifiedPrice());
        assertNull(event.getPriceModificationReason());
        assertFalse(event.isPriceModified());
        assertFalse(event.isCancelled());
    }

    @Test
    void preTransactionTracksPriceModificationAndCancellationIndependently() {
        PreShopTransactionEvent event = new PreShopTransactionEvent(
                null,
                "market",
                "world_nether",
                new ItemStack(Material.EMERALD),
                1,
                10.0D
        );

        event.setModifiedPrice(7.5D, "reputation-discount");
        event.setCancelled(true);

        assertEquals(10.0D, event.getOriginalPrice());
        assertEquals(7.5D, event.getModifiedPrice());
        assertEquals("reputation-discount", event.getPriceModificationReason());
        assertTrue(event.isPriceModified());
        assertTrue(event.isCancelled());

        event.setCancelled(false);
        assertFalse(event.isCancelled());
        assertEquals(7.5D, event.getModifiedPrice());
    }

    @Test
    void postTransactionExposesImmutableTransactionSnapshot() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT, 6);
        PostShopTransactionEvent event = new PostShopTransactionEvent(
                null,
                LANDLORD_ID,
                "mall-a",
                "world",
                item,
                3,
                42.75D
        );

        assertNull(event.getBuyer());
        assertEquals(LANDLORD_ID, event.getLandlordId());
        assertEquals("mall-a", event.getRegionId());
        assertEquals("world", event.getWorldName());
        assertSame(item, event.getItem());
        assertEquals(3, event.getQuantity());
        assertEquals(42.75D, event.getPricePaid());
    }

    @Test
    void eventsExposeTheirCanonicalStaticHandlerLists() {
        PreShopTransactionEvent pre = new PreShopTransactionEvent(
                null, "r", "w", new ItemStack(Material.STONE), 1, 1.0D
        );
        PostShopTransactionEvent post = new PostShopTransactionEvent(
                null, LANDLORD_ID, "r", "w", new ItemStack(Material.STONE), 1, 1.0D
        );

        assertSame(PreShopTransactionEvent.getHandlerList(), pre.getHandlers());
        assertSame(PostShopTransactionEvent.getHandlerList(), post.getHandlers());
    }
}
