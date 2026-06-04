package dev.enthusia.itemshops.data;

import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredShopTest {
    @Test
    void snapshotRestoresLiveShopFields() {
        UUID owner = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        Shop shop = new Shop(
                UUID.randomUUID(),
                new Pos("world", 1, 65, 2),
                new Pos("world", 3, 65, 4),
                owner,
                new ItemStack(Material.DIAMOND, 2),
                new ItemStack(Material.EMERALD, 5)
        );
        shop.addTrusted(trusted);
        shop.setHopperAllowIn(true);
        shop.setHopperAllowOut(false);
        shop.setSearchEnabled(false);
        long frozenUntil = System.currentTimeMillis() + 12345L;
        shop.freezeUntil(frozenUntil);

        Shop restored = StoredShop.from(shop).toShop();

        assertNotNull(restored);
        assertEquals(shop.id(), restored.id());
        assertEquals(shop.sign(), restored.sign());
        assertEquals(shop.container(), restored.container());
        assertEquals(owner, restored.owner());
        assertEquals(Material.DIAMOND, restored.sell().getType());
        assertEquals(2, restored.sell().getAmount());
        assertEquals(Material.EMERALD, restored.cost().getType());
        assertEquals(5, restored.cost().getAmount());
        assertTrue(restored.trusted().contains(trusted));
        assertTrue(restored.isHopperAllowIn());
        assertTrue(restored.isFrozen(System.currentTimeMillis()));
        assertEquals(frozenUntil, restored.frozenUntilMs());
    }
}
