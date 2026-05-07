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

    private final Type type;
    private final long timestampMs;
    private final UUID shopId;
    private final UUID ownerId;
    private final String ownerName;
    private final UUID actorId;
    private final String actorName;
    private final String worldName;
    private final String location;
    private final String soldItem;
    private final int soldAmount;
    private final int trades;
    private final double chargedValue;
    private final String costItem;
    private final int costAmount;
    private final String reason;

    private ShopAnalyticsRecord(Builder builder) {
        this.type = builder.type;
        this.timestampMs = builder.timestampMs;
        this.shopId = builder.shopId;
        this.ownerId = builder.ownerId;
        this.ownerName = clean(builder.ownerName);
        this.actorId = builder.actorId;
        this.actorName = clean(builder.actorName);
        this.worldName = clean(builder.worldName);
        this.location = clean(builder.location);
        this.soldItem = clean(builder.soldItem);
        this.soldAmount = Math.max(0, builder.soldAmount);
        this.trades = Math.max(0, builder.trades);
        this.chargedValue = Math.max(0.0D, builder.chargedValue);
        this.costItem = clean(builder.costItem);
        this.costAmount = Math.max(0, builder.costAmount);
        this.reason = clean(builder.reason);
    }

    public static Builder builder(Type type, long timestampMs) {
        return new Builder(type, timestampMs);
    }

    public Type type() {
        return type;
    }

    public long timestampMs() {
        return timestampMs;
    }

    public UUID shopId() {
        return shopId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorName() {
        return actorName;
    }

    public String worldName() {
        return worldName;
    }

    public String location() {
        return location;
    }

    public String soldItem() {
        return soldItem;
    }

    public int soldAmount() {
        return soldAmount;
    }

    public int trades() {
        return trades;
    }

    public double chargedValue() {
        return chargedValue;
    }

    public String costItem() {
        return costItem;
    }

    public int costAmount() {
        return costAmount;
    }

    public String reason() {
        return reason;
    }

    public boolean involves(UUID playerId) {
        return playerId != null && (playerId.equals(ownerId) || playerId.equals(actorId));
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", type.name());
        map.put("timestampMs", timestampMs);
        putUuid(map, "shopId", shopId);
        putUuid(map, "ownerId", ownerId);
        map.put("ownerName", ownerName);
        putUuid(map, "actorId", actorId);
        map.put("actorName", actorName);
        map.put("worldName", worldName);
        map.put("location", location);
        map.put("soldItem", soldItem);
        map.put("soldAmount", soldAmount);
        map.put("trades", trades);
        map.put("chargedValue", chargedValue);
        map.put("costItem", costItem);
        map.put("costAmount", costAmount);
        map.put("reason", reason);
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
        private final Type type;
        private final long timestampMs;
        private UUID shopId;
        private UUID ownerId;
        private String ownerName;
        private UUID actorId;
        private String actorName;
        private String worldName;
        private String location;
        private String soldItem;
        private int soldAmount;
        private int trades;
        private double chargedValue;
        private String costItem;
        private int costAmount;
        private String reason;

        private Builder(Type type, long timestampMs) {
            this.type = type;
            this.timestampMs = timestampMs;
        }

        public Builder shopId(UUID shopId) {
            this.shopId = shopId;
            return this;
        }

        public Builder owner(UUID ownerId, String ownerName) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            return this;
        }

        public Builder actor(UUID actorId, String actorName) {
            this.actorId = actorId;
            this.actorName = actorName;
            return this;
        }

        public Builder worldName(String worldName) {
            this.worldName = worldName;
            return this;
        }

        public Builder location(String location) {
            this.location = location;
            return this;
        }

        public Builder soldItem(String soldItem) {
            this.soldItem = soldItem;
            return this;
        }

        public Builder soldAmount(int soldAmount) {
            this.soldAmount = soldAmount;
            return this;
        }

        public Builder trades(int trades) {
            this.trades = trades;
            return this;
        }

        public Builder chargedValue(double chargedValue) {
            this.chargedValue = chargedValue;
            return this;
        }

        public Builder costItem(String costItem) {
            this.costItem = costItem;
            return this;
        }

        public Builder costAmount(int costAmount) {
            this.costAmount = costAmount;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public ShopAnalyticsRecord build() {
            return new ShopAnalyticsRecord(this);
        }
    }
}
