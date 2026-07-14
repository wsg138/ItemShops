package dev.enthusia.itemshops.websync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebsiteSyncOutboxTest {
    @TempDir Path temporary;

    @Test
    void epochRevisionsAndShopIdsSurviveRestart() throws Exception {
        Path database = temporary.resolve("outbox.db");
        String epoch;
        UUID shop = UUID.fromString("00000000-0000-4000-8000-000000000010");
        int publicId;
        try (WebsiteSyncOutbox outbox = new WebsiteSyncOutbox(database.toFile())) {
            epoch = outbox.serverEpoch();
            assertEquals(1, outbox.nextSnapshotRevision());
            assertEquals(1, outbox.nextStallRevision("stall1"));
            publicId = outbox.publicShopId(shop);
        }
        try (WebsiteSyncOutbox outbox = new WebsiteSyncOutbox(database.toFile())) {
            assertEquals(epoch, outbox.serverEpoch());
            assertEquals(1, outbox.snapshotRevision());
            assertEquals(2, outbox.nextStallRevision("stall1"));
            assertEquals(publicId, outbox.publicShopId(shop));
            assertNotEquals(publicId, outbox.publicShopId(UUID.fromString("00000000-0000-4000-8000-000000000011")));
        }
    }

    @Test
    void coalescesLatestStateAndReusesEventIdForRetry() throws Exception {
        try (WebsiteSyncOutbox outbox = new WebsiteSyncOutbox(temporary.resolve("coalesce.db").toFile())) {
            outbox.enqueue("stall1", 1, "one", "hash-one", "event-one");
            outbox.enqueue("stall1", 2, "two", "hash-two", "event-two");
            assertEquals(1, outbox.pendingCount());
            WebsiteSyncOutbox.Pending pending = outbox.ready(System.currentTimeMillis(), 10).getFirst();
            assertEquals("event-two", pending.eventId());
            assertEquals("two", pending.payload());
            outbox.failed(pending, 0);
            assertEquals("event-two", outbox.ready(System.currentTimeMillis(), 10).getFirst().eventId());
            outbox.acknowledge(outbox.ready(System.currentTimeMillis(), 10).getFirst());
            assertEquals(0, outbox.pendingCount());
            assertEquals("hash-two", outbox.acknowledgedHash("stall1"));
        }
    }

    @Test
    void retryBackoffIsBounded() {
        assertEquals(5_000, WebsiteSyncOutbox.retryDelayMillis(0, 5, 300));
        assertEquals(300_000, WebsiteSyncOutbox.retryDelayMillis(30, 5, 300));
        assertTrue(WebsiteSyncOutbox.positiveUuidHash(UUID.randomUUID()) > 0);
    }

    @Test
    void fullAcknowledgementDoesNotDeleteNewerPendingState() throws Exception {
        MarketDtos.Stall captured = new MarketDtos.Stall("stall1", "building-1", 1,
                new MarketDtos.Location("world", 1, 2, 3),
                new MarketDtos.Owner("NONE", null, null, "Unowned stall", null, new MarketDtos.Avatar("NONE", null, null, null)),
                null, null, List.of(), List.of());
        try (WebsiteSyncOutbox outbox = new WebsiteSyncOutbox(temporary.resolve("full-race.db").toFile())) {
            outbox.enqueue("stall1", 2, "newer", "different-hash", "newer-event");
            outbox.markFullAcknowledged(1, List.of(captured));
            assertEquals(1, outbox.pendingCount());
            assertEquals("newer-event", outbox.ready(System.currentTimeMillis(), 10).getFirst().eventId());
        }
    }
}
