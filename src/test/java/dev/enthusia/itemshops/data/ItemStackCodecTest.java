package dev.enthusia.itemshops.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStackCodecTest {
    @BeforeEach
    void setUpServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    @Test
    void encodesItemTypeAndAmount() {
        ItemStack original = new ItemStack(Material.DIAMOND, 17);

        String encoded = ItemStackCodec.encode(original);

        assertFalse(encoded.isBlank());
        assertTrue(encoded.contains("DIAMOND"));
        assertTrue(encoded.contains("amount: 17"));
    }

    @Test
    void invalidYamlReturnsNull() {
        assertNull(ItemStackCodec.decode("item: ["));
    }
}
