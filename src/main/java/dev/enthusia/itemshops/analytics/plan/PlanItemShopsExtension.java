package dev.enthusia.itemshops.analytics.plan;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.annotation.DataBuilderProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.builder.ExtensionDataBuilder;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import com.djrapitops.plan.extension.icon.Icon;
import com.djrapitops.plan.extension.table.Table;
import com.djrapitops.plan.extension.table.TableColumnFormat;
import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.analytics.ShopAnalyticsRecord;
import dev.enthusia.itemshops.analytics.ShopAnalyticsStore;
import dev.enthusia.itemshops.model.Shop;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

@PluginInfo(
        name = "ItemShops",
        iconName = "store",
        iconFamily = Family.SOLID,
        color = Color.GREEN
)
public final class PlanItemShopsExtension implements DataExtension {
    private static final Duration DAY = Duration.ofDays(1);
    private static final Duration WEEK = Duration.ofDays(7);
    private static final Duration MONTH = Duration.ofDays(30);
    private static final long SHOP_SNAPSHOT_TTL_MS = 10_000L;
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private final ItemShopsPlugin plugin;
    private final ShopAnalyticsStore analyticsStore;
    private volatile List<Shop> cachedShopSnapshot = List.of();
    private volatile long cachedShopSnapshotUntilMs;

    public PlanItemShopsExtension(ItemShopsPlugin plugin, ShopAnalyticsStore analyticsStore) {
        this.plugin = plugin;
        this.analyticsStore = analyticsStore;
    }

    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[]{CallEvents.PLAYER_JOIN, CallEvents.PLAYER_LEAVE, CallEvents.SERVER_EXTENSION_REGISTER};
    }

    @DataBuilderProvider
    public ExtensionDataBuilder serverData() {
        ExtensionDataBuilder builder = newExtensionDataBuilder();
        Collection<Shop> shops = shopSnapshot();

        builder.addValue(Long.class, valueBuilder("Active Shops")
                .description("Current number of registered item shops")
                .priority(100)
                .icon("store", Family.SOLID, Color.GREEN)
                .buildNumber((long) shops.size()));
        builder.addValue(Long.class, valueBuilder("Searchable Shops")
                .description("Current shops visible in item search")
                .priority(90)
                .icon("search", Family.SOLID, Color.LIGHT_BLUE)
                .buildNumber(countShops(shops, Shop::isSearchEnabled)));
        builder.addValue(Long.class, valueBuilder("Frozen Shops")
                .description("Current shops paused by staff")
                .priority(80)
                .icon("pause-circle", Family.SOLID, Color.ORANGE)
                .buildNumber(countShops(shops, Shop::isFrozen)));
        builder.addValue(Long.class, valueBuilder("Purchases 24h")
                .description("Successful purchases recorded in the last 24 hours")
                .priority(70)
                .icon("shopping-cart", Family.SOLID, Color.GREEN)
                .buildNumber(analyticsStore.count(ShopAnalyticsRecord.Type.PURCHASE, DAY)));
        builder.addValue(Long.class, valueBuilder("Purchases 7d")
                .description("Successful purchases recorded in the last 7 days")
                .priority(60)
                .icon("shopping-cart", Family.SOLID, Color.LIGHT_GREEN)
                .buildNumber(analyticsStore.count(ShopAnalyticsRecord.Type.PURCHASE, WEEK)));
        builder.addValue(Long.class, valueBuilder("Purchases 30d")
                .description("Successful purchases recorded in the last 30 days")
                .priority(50)
                .icon("shopping-cart", Family.SOLID, Color.BLUE)
                .buildNumber(analyticsStore.count(ShopAnalyticsRecord.Type.PURCHASE, MONTH)));
        builder.addValue(Long.class, valueBuilder("Shops Created 7d")
                .description("Item shops created in the last 7 days")
                .priority(40)
                .icon("plus-circle", Family.SOLID, Color.GREEN)
                .buildNumber(analyticsStore.count(ShopAnalyticsRecord.Type.SHOP_CREATED, WEEK)));
        builder.addValue(Long.class, valueBuilder("Shops Deleted 7d")
                .description("Item shops deleted or cleaned up in the last 7 days")
                .priority(30)
                .icon("trash", Family.SOLID, Color.RED)
                .buildNumber(analyticsStore.count(ShopAnalyticsRecord.Type.SHOP_DELETED, WEEK)));
        builder.addValue(Long.class, valueBuilder("Stock Depleted 7d")
                .description("Shop stock depletion events in the last 7 days")
                .priority(20)
                .icon("box-open", Family.SOLID, Color.AMBER)
                .buildNumber(analyticsStore.count(ShopAnalyticsRecord.Type.STOCK_DEPLETED, WEEK)));

        int limit = plugin.pluginConfig().analyticsRecentTableLimit();
        builder.addTable("Recent Purchases", purchaseTable(analyticsStore.recent(ShopAnalyticsRecord.Type.PURCHASE, limit), true), Color.GREEN);
        builder.addTable("Recent Shop Changes", shopChangesTable(limit), Color.BLUE);
        builder.addTable("Recent Stock Depletion", stockDepletionTable(analyticsStore.recent(ShopAnalyticsRecord.Type.STOCK_DEPLETED, limit)), Color.ORANGE);
        return builder;
    }

    @DataBuilderProvider
    public ExtensionDataBuilder playerData(UUID playerId) {
        ExtensionDataBuilder builder = newExtensionDataBuilder();
        List<Shop> owned = ownedShopSnapshot(playerId);

        builder.addValue(Long.class, valueBuilder("Owned Shops")
                .description("Current ItemShops owned by this player")
                .priority(100)
                .icon("store", Family.SOLID, Color.GREEN)
                .showInPlayerTable()
                .buildNumber((long) owned.size()));
        builder.addValue(Long.class, valueBuilder("Searchable Owned Shops")
                .description("Current owned shops visible in item search")
                .priority(90)
                .icon("search", Family.SOLID, Color.LIGHT_BLUE)
                .buildNumber(countShops(owned, Shop::isSearchEnabled)));
        builder.addValue(Long.class, valueBuilder("Frozen Owned Shops")
                .description("Current owned shops paused by staff")
                .priority(80)
                .icon("pause-circle", Family.SOLID, Color.ORANGE)
                .buildNumber(countShops(owned, Shop::isFrozen)));
        builder.addValue(Long.class, valueBuilder("Purchases 24h")
                .description("Successful ItemShops purchases by this player in the last 24 hours")
                .priority(70)
                .icon("shopping-cart", Family.SOLID, Color.GREEN)
                .buildNumber(purchaseCount(playerId, DAY)));
        builder.addValue(Long.class, valueBuilder("Purchases 7d")
                .description("Successful ItemShops purchases by this player in the last 7 days")
                .priority(60)
                .icon("shopping-cart", Family.SOLID, Color.LIGHT_GREEN)
                .buildNumber(purchaseCount(playerId, WEEK)));
        builder.addValue(Long.class, valueBuilder("Sales 7d")
                .description("Successful ItemShops purchases from this player's shops in the last 7 days")
                .priority(50)
                .icon("cash-register", Family.SOLID, Color.BLUE)
                .buildNumber(saleCount(playerId, WEEK)));
        builder.addValue(String.class, valueBuilder("Last Purchase")
                .description("Most recent ItemShops purchase by this player")
                .priority(40)
                .icon("clock", Family.SOLID, Color.GREY)
                .buildString(lastActivity(playerId, record -> playerId.equals(record.actorId()))));
        builder.addValue(String.class, valueBuilder("Last Sale")
                .description("Most recent ItemShops sale from this player's shops")
                .priority(30)
                .icon("clock", Family.SOLID, Color.GREY)
                .buildString(lastActivity(playerId, record -> playerId.equals(record.ownerId()))));

        int limit = plugin.pluginConfig().analyticsRecentTableLimit();
        builder.addTable("Recent Purchases", purchaseTable(
                analyticsStore.recentForPlayer(playerId, ShopAnalyticsRecord.Type.PURCHASE, limit, record -> playerId.equals(record.actorId())),
                false
        ), Color.GREEN);
        builder.addTable("Recent Sales", purchaseTable(
                analyticsStore.recentForPlayer(playerId, ShopAnalyticsRecord.Type.PURCHASE, limit, record -> playerId.equals(record.ownerId())),
                false
        ), Color.BLUE);
        return builder;
    }

    private long purchaseCount(UUID playerId, Duration window) {
        return analyticsStore.countForPlayer(playerId, ShopAnalyticsRecord.Type.PURCHASE, window, record -> playerId.equals(record.actorId()));
    }

    private long saleCount(UUID playerId, Duration window) {
        return analyticsStore.countForPlayer(playerId, ShopAnalyticsRecord.Type.PURCHASE, window, record -> playerId.equals(record.ownerId()));
    }

    private String lastActivity(UUID playerId, Predicate<ShopAnalyticsRecord> roleFilter) {
        ShopAnalyticsRecord record = analyticsStore.latestForPlayer(playerId, ShopAnalyticsRecord.Type.PURCHASE, roleFilter);
        if (record == null) {
            return "Never";
        }
        return SHORT_DATE.format(Instant.ofEpochMilli(record.timestampMs())) + " - " + record.soldItem();
    }

    private Table purchaseTable(List<ShopAnalyticsRecord> records, boolean includeOwner) {
        Table.Factory table = Table.builder()
                .columnOne("Time", icon("clock", Color.GREY))
                .columnOneFormat(TableColumnFormat.DATE_SECOND)
                .columnTwo("Buyer", icon("user", Color.LIGHT_BLUE))
                .columnTwoFormat(TableColumnFormat.PLAYER_NAME)
                .columnThree(includeOwner ? "Owner" : "Item", icon(includeOwner ? "user-tag" : "box", includeOwner ? Color.BLUE : Color.GREEN))
                .columnThreeFormat(includeOwner ? TableColumnFormat.PLAYER_NAME : TableColumnFormat.NONE)
                .columnFour(includeOwner ? "Item" : "Qty", icon(includeOwner ? "box" : "hashtag", includeOwner ? Color.GREEN : Color.AMBER))
                .columnFive("Charged", icon("coins", Color.AMBER));

        for (ShopAnalyticsRecord record : records) {
            if (includeOwner) {
                table.addRow(record.timestampMs(), displayName(record.actorName(), record.actorId()), displayName(record.ownerName(), record.ownerId()), record.soldItem(), chargedValue(record));
            } else {
                table.addRow(record.timestampMs(), displayName(record.actorName(), record.actorId()), record.soldItem(), record.soldAmount(), chargedValue(record));
            }
        }
        return table.build();
    }

    private Table shopChangesTable(int limit) {
        Table.Factory table = Table.builder()
                .columnOne("Time", icon("clock", Color.GREY))
                .columnOneFormat(TableColumnFormat.DATE_SECOND)
                .columnTwo("Action", icon("exchange-alt", Color.BLUE))
                .columnThree("Owner", icon("user", Color.LIGHT_BLUE))
                .columnThreeFormat(TableColumnFormat.PLAYER_NAME)
                .columnFour("Item", icon("box", Color.GREEN))
                .columnFive("Details", icon("map-marker-alt", Color.ORANGE));

        List<ShopAnalyticsRecord> changes = analyticsStore.recent(record ->
                record.type() == ShopAnalyticsRecord.Type.SHOP_CREATED || record.type() == ShopAnalyticsRecord.Type.SHOP_DELETED,
                limit
        );
        for (ShopAnalyticsRecord record : changes) {
            String details = record.type() == ShopAnalyticsRecord.Type.SHOP_DELETED && !record.reason().isBlank()
                    ? record.reason()
                    : record.location();
            table.addRow(record.timestampMs(), changeLabel(record.type()), displayName(record.ownerName(), record.ownerId()), record.soldItem(), details);
        }
        return table.build();
    }

    private Table stockDepletionTable(List<ShopAnalyticsRecord> records) {
        Table.Factory table = Table.builder()
                .columnOne("Time", icon("clock", Color.GREY))
                .columnOneFormat(TableColumnFormat.DATE_SECOND)
                .columnTwo("Owner", icon("user", Color.LIGHT_BLUE))
                .columnTwoFormat(TableColumnFormat.PLAYER_NAME)
                .columnThree("Buyer", icon("shopping-cart", Color.GREEN))
                .columnThreeFormat(TableColumnFormat.PLAYER_NAME)
                .columnFour("Item", icon("box-open", Color.ORANGE))
                .columnFive("Location", icon("map-marker-alt", Color.BLUE));

        for (ShopAnalyticsRecord record : records) {
            table.addRow(record.timestampMs(), displayName(record.ownerName(), record.ownerId()), displayName(record.actorName(), record.actorId()), record.soldItem(), record.location());
        }
        return table.build();
    }

    private long countShops(Collection<Shop> shops, Predicate<Shop> filter) {
        return shops.stream().filter(filter).count();
    }

    private Collection<Shop> shopSnapshot() {
        long now = System.currentTimeMillis();
        List<Shop> cached = cachedShopSnapshot;
        if (now < cachedShopSnapshotUntilMs && !cached.isEmpty()) {
            plugin.performance().planCacheHits.increment();
            return cached;
        }
        plugin.performance().planCacheMisses.increment();
        List<Shop> fresh;
        if (plugin.getServer().isPrimaryThread()) {
            fresh = new ArrayList<>(plugin.shops().all());
        } else {
            try {
                fresh = plugin.getServer().getScheduler()
                        .callSyncMethod(plugin, () -> new ArrayList<>(plugin.shops().all()))
                        .get(2L, TimeUnit.SECONDS);
            } catch (Exception e) {
                plugin.getLogger().fine("Could not snapshot ItemShops state for Plan: " + e.getMessage());
                return cached;
            }
        }
        cachedShopSnapshot = fresh;
        cachedShopSnapshotUntilMs = now + SHOP_SNAPSHOT_TTL_MS;
        return fresh;
    }

    private List<Shop> ownedShopSnapshot(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return shopSnapshot().stream()
                .filter(shop -> playerId.equals(shop.owner()))
                .toList();
    }

    private String chargedValue(ShopAnalyticsRecord record) {
        if (record.chargedValue() <= 0.0D) {
            return "";
        }
        double value = record.chargedValue();
        if (Math.rint(value) == value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String changeLabel(ShopAnalyticsRecord.Type type) {
        return switch (type) {
            case SHOP_CREATED -> "Created";
            case SHOP_DELETED -> "Deleted";
            default -> type.name();
        };
    }

    private String displayName(String name, UUID uuid) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return uuid == null ? "" : uuid.toString();
    }

    private Icon icon(String name, Color color) {
        return Icon.called(name).of(Family.SOLID).of(color).build();
    }
}
