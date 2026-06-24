package dev.enthusia.itemshops.analytics;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ShopAnalyticsRecord {
    public enum Type {
        PURCHASE,
        SHOP_CREATED,
        SHOP_DELETED,
        STOCK_DEPLETED
    }

    private final Type recordType;
    private final long recordTimestampMs;
    private final UUID recordShopId;
    private final UUID recordOwnerId;
    private final String recordOwnerName;
    private final UUID recordActorId;
    private final String recordActorName;
    private final String recordWorldName;
    private final String recordLocation;
    private final String recordSoldItem;
    private final int recordSoldAmount;
    private final int recordTrades;
    private final double recordChargedValue;
    private final String recordCostItem;
    private final int recordCostAmount;
    private final String recordReason;

    private ShopAnalyticsRecord(Builder builder) {
        this.recordType = builder.builderType;
        this.recordTimestampMs = builder.builderTimestampMs;
        this.recordShopId = builder.builderShopId;
        this.recordOwnerId = builder.builderOwnerId;
        this.recordOwnerName = clean(builder.builderOwnerName);
        this.recordActorId = builder.builderActorId;
        this.recordActorName = clean(builder.builderActorName);
        this.recordWorldName = clean(builder.builderWorldName);
        this.recordLocation = clean(builder.builderLocation);
        this.recordSoldItem = clean(builder.builderSoldItem);
        this.recordSoldAmount = Math.max(0, builder.builderSoldAmount);
        this.recordTrades = Math.max(0, builder.builderTrades);
        this.recordChargedValue = Math.max(0.0D, builder.builderChargedValue);
        this.recordCostItem = clean(builder.builderCostItem);
        this.recordCostAmount = Math.max(0, builder.builderCostAmount);
        this.recordReason = clean(builder.builderReason);
    }

    public static Builder builder(Type type, long timestampMs) {
        return new Builder(type, timestampMs);
    }

    public Type type() {
        return recordType;
    }

    public long timestampMs() {
        return recordTimestampMs;
    }

    public UUID shopId() {
        return recordShopId;
    }

    public UUID ownerId() {
        return recordOwnerId;
    }

    public String ownerName() {
        return recordOwnerName;
    }

    public UUID actorId() {
        return recordActorId;
    }

    public String actorName() {
        return recordActorName;
    }

    public String worldName() {
        return recordWorldName;
    }

    public String location() {
        return recordLocation;
    }

    public String soldItem() {
        return recordSoldItem;
    }

    public int soldAmount() {
        return recordSoldAmount;
    }

    public int trades() {
        return recordTrades;
    }

    public double chargedValue() {
        return recordChargedValue;
    }

    public String costItem() {
        return recordCostItem;
    }

    public int costAmount() {
        return recordCostAmount;
    }

    public String reason() {
        return recordReason;
    }

    public boolean involves(UUID playerId) {
        return playerId != null && (playerId.equals(recordOwnerId) || playerId.equals(recordActorId));
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", recordType.name());
        map.put("timestampMs", recordTimestampMs);
        putUuid(map, "shopId", recordShopId);
        putUuid(map, "ownerId", recordOwnerId);
        map.put("ownerName", recordOwnerName);
        putUuid(map, "actorId", recordActorId);
        map.put("actorName", recordActorName);
        map.put("worldName", recordWorldName);
        map.put("location", recordLocation);
        map.put("soldItem", recordSoldItem);
        map.put("soldAmount", recordSoldAmount);
        map.put("trades", recordTrades);
        map.put("chargedValue", recordChargedValue);
        map.put("costItem", recordCostItem);
        map.put("costAmount", recordCostAmount);
        map.put("reason", recordReason);
        return map;
    }

    public static ShopAnalyticsRecord deserialize(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Type type;
        try {
            type = Type.valueOf(section.getString("type", "").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        long timestampMs = section.getLong("timestampMs", 0L);
        if (timestampMs <= 0L) {
            return null;
        }
        return builder(type, timestampMs)
                .shopId(readUuid(section, "shopId"))
                .owner(readUuid(section, "ownerId"), section.getString("ownerName", ""))
                .actor(readUuid(section, "actorId"), section.getString("actorName", ""))
                .worldName(section.getString("worldName", ""))
                .location(section.getString("location", ""))
                .soldItem(section.getString("soldItem", ""))
                .soldAmount(section.getInt("soldAmount", 0))
                .trades(section.getInt("trades", 0))
                .chargedValue(section.getDouble("chargedValue", 0.0D))
                .costItem(section.getString("costItem", ""))
                .costAmount(section.getInt("costAmount", 0))
                .reason(section.getString("reason", ""))
                .build();
    }

    private static void putUuid(Map<String, Object> map, String key, UUID uuid) {
        map.put(key, uuid == null ? "" : uuid.toString());
    }

    private static UUID readUuid(ConfigurationSection section, String key) {
        String raw = section.getString(key, "");
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }

    public static final class Builder {
        private final Type builderType;
        private final long builderTimestampMs;
        private UUID builderShopId;
        private UUID builderOwnerId;
        private String builderOwnerName;
        private UUID builderActorId;
        private String builderActorName;
        private String builderWorldName;
        private String builderLocation;
        private String builderSoldItem;
        private int builderSoldAmount;
        private int builderTrades;
        private double builderChargedValue;
        private String builderCostItem;
        private int builderCostAmount;
        private String builderReason;

        private Builder(Type type, long timestampMs) {
            this.builderType = type;
            this.builderTimestampMs = timestampMs;
        }

        public Builder shopId(UUID shopId) {
            this.builderShopId = shopId;
            return this;
        }

        public Builder owner(UUID ownerId, String ownerName) {
            this.builderOwnerId = ownerId;
            this.builderOwnerName = ownerName;
            return this;
        }

        public Builder actor(UUID actorId, String actorName) {
            this.builderActorId = actorId;
            this.builderActorName = actorName;
            return this;
        }

        public Builder worldName(String worldName) {
            this.builderWorldName = worldName;
            return this;
        }

        public Builder location(String location) {
            this.builderLocation = location;
            return this;
        }

        public Builder soldItem(String soldItem) {
            this.builderSoldItem = soldItem;
            return this;
        }

        public Builder soldAmount(int soldAmount) {
            this.builderSoldAmount = soldAmount;
            return this;
        }

        public Builder trades(int trades) {
            this.builderTrades = trades;
            return this;
        }

        public Builder chargedValue(double chargedValue) {
            this.builderChargedValue = chargedValue;
            return this;
        }

        public Builder costItem(String costItem) {
            this.builderCostItem = costItem;
            return this;
        }

        public Builder costAmount(int costAmount) {
            this.builderCostAmount = costAmount;
            return this;
        }

        public Builder reason(String reason) {
            this.builderReason = reason;
            return this;
        }

        public ShopAnalyticsRecord build() {
            return new ShopAnalyticsRecord(this);
        }
    }
}
