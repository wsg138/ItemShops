package dev.enthusia.itemshops;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemShopsPluginSmokeTest {
    @BeforeEach
    void setUpServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDownServer() {
        MockBukkit.unmock();
    }

    @Test
    void enablesWithDefaultSqliteStorage() {
        ItemShopsPlugin plugin = MockBukkit.load(ItemShopsPlugin.class);

        assertTrue(plugin.isEnabled());
        assertEquals("sqlite", plugin.pluginConfig().storageType());
        assertTrue(new File(plugin.getDataFolder(), plugin.pluginConfig().sqliteFileName()).exists());
    }
}
