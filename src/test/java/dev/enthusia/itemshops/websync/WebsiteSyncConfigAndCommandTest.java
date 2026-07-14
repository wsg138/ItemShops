package dev.enthusia.itemshops.websync;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.config.ConfigMigrator;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteSyncConfigAndCommandTest {
    private ServerMock server;
    private ItemShopsPlugin plugin;

    @BeforeEach void start() { server = MockBukkit.mock(); plugin = MockBukkit.load(ItemShopsPlugin.class); }
    @AfterEach void stop() { MockBukkit.unmock(); }

    @Test
    void defaultFileExistsWithBlankSecret() {
        File file = new File(plugin.getDataFolder(), "website-sync.yml");
        assertTrue(file.isFile());
        assertEquals("", YamlConfiguration.loadConfiguration(file).getString("sync-secret"));
    }

    @Test
    void migrationPreservesSecretAndCustomValuesAndIsIdempotent() throws Exception {
        File file = new File(plugin.getDataFolder(), "website-sync.yml");
        YamlConfiguration partial = new YamlConfiguration();
        partial.set("config-version", 0);
        partial.set("sync-secret", "part-one,part-two,! punctuation");
        partial.set("endpoint", "https://example.invalid");
        partial.set("timing.startup-delay-seconds", 77);
        partial.save(file);

        new ConfigMigrator(plugin).migrateStartupFiles();
        YamlConfiguration migrated = YamlConfiguration.loadConfiguration(file);
        assertEquals("part-one,part-two,! punctuation", migrated.getString("sync-secret"));
        assertEquals("https://example.invalid", migrated.getString("endpoint"));
        assertEquals(77, migrated.getInt("timing.startup-delay-seconds"));
        assertNotNull(migrated.get("retry.maximum-delay-seconds"));
        File backupDirectory = new File(plugin.getDataFolder(), "backups");
        long backups = Files.list(backupDirectory.toPath()).filter(path -> path.getFileName().toString().startsWith("website-sync.yml.")).count();
        assertTrue(backups >= 1);

        new ConfigMigrator(plugin).migrateStartupFiles();
        long afterSecondRun = Files.list(backupDirectory.toPath()).filter(path -> path.getFileName().toString().startsWith("website-sync.yml.")).count();
        assertEquals(backups, afterSecondRun);
    }

    @Test
    void secretCommandPreservesCommaAndNeverEchoesSecret() {
        String secret = "alpha,beta!$%long-value";
        var console = server.getConsoleSender();
        assertTrue(server.dispatchCommand(console, "shopmarket sync secret " + secret));
        assertEquals(secret, YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "website-sync.yml")).getString("sync-secret"));
        String confirmation = console.nextMessage();
        assertNotNull(confirmation);
        assertFalse(confirmation.contains(secret));

        assertTrue(server.dispatchCommand(console, "shopmarket sync status"));
        for (int i = 0; i < 10; i++) {
            String line = console.nextMessage();
            if (line == null) break;
            assertFalse(line.contains(secret));
        }
    }

    @Test
    void clearSecretRequiresConfirmationAndUnauthorizedPlayerIsRejected() throws Exception {
        plugin.websiteMarketSync().settings().setSecret("temporary,test-secret");
        var console = server.getConsoleSender();
        server.dispatchCommand(console, "shopmarket sync clear-secret");
        assertTrue(plugin.websiteMarketSync().settings().secretConfigured());
        server.dispatchCommand(console, "shopmarket sync clear-secret confirm");
        assertFalse(plugin.websiteMarketSync().settings().secretConfigured());

        var player = server.addPlayer();
        server.dispatchCommand(player, "shopmarket sync secret forbidden");
        assertFalse(plugin.websiteMarketSync().settings().secretConfigured());
    }
}
