package dev.enthusia.itemshops.websync;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSnapshotBuilderTest {
    @TempDir Path temporary;
    private ItemShopsPlugin plugin;

    @BeforeEach void start() { MockBukkit.mock(); plugin = MockBukkit.load(ItemShopsPlugin.class); }
    @AfterEach void stop() { MockBukkit.unmock(); }

    @Test
    void safeProviderProducesExactlySeventyOneStrictPublicStalls() throws Exception {
        try (WebsiteSyncOutbox outbox = new WebsiteSyncOutbox(temporary.resolve("snapshot.db").toFile())) {
            MarketSnapshotBuilder builder = new MarketSnapshotBuilder(CanonicalMarketLayout.load(), new EmptyProvider(), plugin.shops(), outbox);
            List<MarketDtos.Stall> stalls = builder.captureAll();
            assertEquals(71, stalls.size());
            assertEquals(71, stalls.stream().map(MarketDtos.Stall::id).distinct().count());
            String json = MarketJson.encode(stalls);
            assertFalse(json.contains("container"));
            assertFalse(json.contains("trusted"));
            assertFalse(json.contains("permission"));
            assertFalse(json.contains("\"source\":null"));
            assertFalse(json.contains("\"includesOuterLayer\":null"));
            assertFalse(json.contains("\"url\":null"));
            assertTrue(json.contains("\"avatarUrl\":null"));
        }
    }

    @Test
    void unavailableProviderPreventsUnsafeFullCapture() throws Exception {
        try (WebsiteSyncOutbox outbox = new WebsiteSyncOutbox(temporary.resolve("blocked.db").toFile())) {
            MarketSnapshotBuilder builder = new MarketSnapshotBuilder(CanonicalMarketLayout.load(), new UnavailableOwnershipProvider(), plugin.shops(), outbox);
            assertThrows(IllegalStateException.class, builder::captureAll);
        }
    }

    private static final class EmptyProvider implements StallOwnershipProvider {
        @Override public boolean available() { return true; }
        @Override public String name() { return "test"; }
        @Override public String unavailableReason() { return ""; }
        @Override public StallLease leaseFor(CanonicalMarketLayout.StallDefinition stall) {
            return new StallLease(true, new MarketDtos.Owner("NONE", null, null, "Unowned stall", null,
                    new MarketDtos.Avatar("NONE", null, null, null)), null, null, List.of(), "test-" + stall.id());
        }
    }
}
