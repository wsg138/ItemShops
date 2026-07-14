package dev.enthusia.itemshops.websync;

import dev.enthusia.itemshops.util.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalMarketLayoutTest {
    private final CanonicalMarketLayout layout = CanonicalMarketLayout.load();

    @Test
    void containsAllCanonicalStallsAndFifteenBuildings() {
        assertEquals(71, layout.stalls().size());
        assertEquals(71, layout.stalls().stream().map(CanonicalMarketLayout.StallDefinition::id).distinct().count());
        assertEquals(15, layout.stalls().stream().map(CanonicalMarketLayout.StallDefinition::buildingId).distinct().count());
    }

    @Test
    void assignsInsideAndRejectsOutsideOrAmbiguousCoordinates() {
        assertEquals("stall1", layout.assign(new Pos("world", -44, 102, -192)).stall().id());
        assertEquals(CanonicalMarketLayout.Assignment.Status.UNMAPPED, layout.assign(new Pos("world", 0, 64, 0)).status());
        assertEquals(CanonicalMarketLayout.Assignment.Status.AMBIGUOUS, layout.assign(new Pos("world", 63, 118, -269)).status());
        assertTrue(layout.assign(new Pos("world_nether", -44, 102, -192)).candidates().isEmpty());
    }
}
