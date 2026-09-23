package dev.enthusia.itemshops.analytics;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopAnalyticsRecordTest {
    private static final UUID SHOP_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    @Test
    void builderNormalizesNegativeAmountsAndNullText() {
        ShopAnalyticsRecord record = ShopAnalyticsRecord.builder(ShopAnalyticsRecord.Type.PURCHASE, 1234L)
                .shopId(SHOP_ID)
                .owner(OWNER_ID, null)
                .actor(ACTOR_ID, null)
                .worldName(null)
                .location(null)
                .soldItem(null)
                .soldAmount(-4)
                .trades(-2)
                .chargedValue(-9.5D)
                .costItem(null)
                .costAmount(-8)
                .reason(null)
                .build();

        assertEquals("", record.ownerName());
        assertEquals("", record.actorName());
        assertEquals("", record.worldName());
        assertEquals("", record.location());
        assertEquals("", record.soldItem());
        assertEquals(0, record.soldAmount());
        assertEquals(0, record.trades());
        assertEquals(0.0D, record.chargedValue());
        assertEquals("", record.costItem());
        assertEquals(0, record.costAmount());
        assertEquals("", record.reason());
    }

    @Test
    void involvementMatchesOwnerOrActorOnly() {
        ShopAnalyticsRecord record = ShopAnalyticsRecord.builder(ShopAnalyticsRecord.Type.PURCHASE, 1234L)
                .owner(OWNER_ID, "Owner")
                .actor(ACTOR_ID, "Buyer")
                .build();

        assertTrue(record.involves(OWNER_ID));
        assertTrue(record.involves(ACTOR_ID));
        assertFalse(record.involves(UUID.fromString("10000000-0000-0000-0000-000000000004")));
        assertFalse(record.involves(null));
    }

    @Test
    void serializedRecordRoundTripsThroughBukkitConfiguration() {
        ShopAnalyticsRecord original = ShopAnalyticsRecord.builder(ShopAnalyticsRecord.Type.PURCHASE, 987654321L)
                .shopId(SHOP_ID)
                .owner(OWNER_ID, "Owner")
                .actor(ACTOR_ID, "Buyer")
                .worldName("world")
                .location("10,64,-5")
                .soldItem("DIAMOND")
                .soldAmount(3)
                .trades(2)
                .chargedValue(125.5D)
                .costItem("EMERALD")
                .costAmount(7)
                .reason("purchase")
                .build();

        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("record", original.serialize());
        ShopAnalyticsRecord restored = ShopAnalyticsRecord.deserialize(section);

        assertEquals(original.type(), restored.type());
        assertEquals(original.timestampMs(), restored.timestampMs());
        assertEquals(original.shopId(), restored.shopId());
        assertEquals(original.ownerId(), restored.ownerId());
        assertEquals(original.ownerName(), restored.ownerName());
        assertEquals(original.actorId(), restored.actorId());
        assertEquals(original.actorName(), restored.actorName());
        assertEquals(original.worldName(), restored.worldName());
        assertEquals(original.location(), restored.location());
        assertEquals(original.soldItem(), restored.soldItem());
        assertEquals(original.soldAmount(), restored.soldAmount());
        assertEquals(original.trades(), restored.trades());
        assertEquals(original.chargedValue(), restored.chargedValue());
        assertEquals(original.costItem(), restored.costItem());
        assertEquals(original.costAmount(), restored.costAmount());
        assertEquals(original.reason(), restored.reason());
    }

    @Test
    void invalidSerializedTypeOrTimestampFailsClosed() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection invalidType = yaml.createSection("invalidType", Map.of(
                "type", "not-a-type",
                "timestampMs", 100L
        ));
        ConfigurationSection zeroTimestamp = yaml.createSection("zeroTimestamp", Map.of(
                "type", "PURCHASE",
                "timestampMs", 0L
        ));
        ConfigurationSection negativeTimestamp = yaml.createSection("negativeTimestamp", Map.of(
                "type", "SHOP_CREATED",
                "timestampMs", -1L
        ));

        assertNull(ShopAnalyticsRecord.deserialize(null));
        assertNull(ShopAnalyticsRecord.deserialize(invalidType));
        assertNull(ShopAnalyticsRecord.deserialize(zeroTimestamp));
        assertNull(ShopAnalyticsRecord.deserialize(negativeTimestamp));
    }

    @Test
    void invalidUuidFieldsAreIgnoredWithoutDiscardingTheRecord() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection section = yaml.createSection("record", Map.of(
                "type", "SHOP_DELETED",
                "timestampMs", 500L,
                "shopId", "bad-uuid",
                "ownerId", "",
                "actorId", "also-bad"
        ));

        ShopAnalyticsRecord record = ShopAnalyticsRecord.deserialize(section);

        assertEquals(ShopAnalyticsRecord.Type.SHOP_DELETED, record.type());
        assertNull(record.shopId());
        assertNull(record.ownerId());
        assertNull(record.actorId());
    }
}
