package dev.enthusia.itemshops.websync;

import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MarketSnapshotBuilder {
    private final CanonicalMarketLayout layout;
    private final StallOwnershipProvider ownership;
    private final ShopManager shops;
    private final WebsiteSyncOutbox outbox;
    private final PublicItemSerializer items = new PublicItemSerializer();

    public MarketSnapshotBuilder(CanonicalMarketLayout layout, StallOwnershipProvider ownership, ShopManager shops, WebsiteSyncOutbox outbox) {
        this.layout = layout; this.ownership = ownership; this.shops = shops; this.outbox = outbox;
    }

    public List<MarketDtos.Stall> captureAll() throws SQLException {
        requireProvider();
        Map<String, List<Shop>> assigned = assignShops(shops.all());
        List<MarketDtos.Stall> snapshot = new ArrayList<>(CanonicalMarketLayout.STALL_COUNT);
        for (CanonicalMarketLayout.StallDefinition definition : layout.stalls()) snapshot.add(capture(definition, assigned.getOrDefault(definition.id(), List.of())));
        if (snapshot.size() != CanonicalMarketLayout.STALL_COUNT) throw new IllegalStateException("Full Market snapshot did not contain 71 stalls");
        return List.copyOf(snapshot);
    }

    public MarketDtos.Stall captureOne(String stallId) throws SQLException {
        requireProvider();
        CanonicalMarketLayout.StallDefinition definition = layout.byId(stallId).orElseThrow(() -> new IllegalArgumentException("Unknown stall ID"));
        return capture(definition, assignShops(shops.all()).getOrDefault(stallId, List.of()));
    }

    private void requireProvider() {
        if (!ownership.available()) throw new IllegalStateException("Ownership provider unavailable: " + ownership.unavailableReason());
    }

    private Map<String, List<Shop>> assignShops(Collection<Shop> current) {
        Map<String, List<Shop>> assigned = new HashMap<>();
        List<String> unsafe = new ArrayList<>();
        for (Shop shop : current) {
            CanonicalMarketLayout.Assignment assignment = layout.assign(shop.sign());
            if (assignment.status() != CanonicalMarketLayout.Assignment.Status.MATCHED) {
                CanonicalMarketLayout.Assignment containerAssignment = layout.assign(shop.container());
                if (containerAssignment.status() == CanonicalMarketLayout.Assignment.Status.MATCHED) assignment = containerAssignment;
            }
            if (assignment.status() == CanonicalMarketLayout.Assignment.Status.MATCHED) {
                assigned.computeIfAbsent(assignment.stall().id(), ignored -> new ArrayList<>()).add(shop);
            } else if (shops.isInMarket(shop.container())) {
                unsafe.add(assignment.status().name());
            }
        }
        if (!unsafe.isEmpty()) throw new IllegalStateException("Public Market contains " + unsafe.size() + " unmatched or ambiguous shop assignments");
        return assigned;
    }

    private MarketDtos.Stall capture(CanonicalMarketLayout.StallDefinition definition, List<Shop> stallShops) throws SQLException {
        StallOwnershipProvider.StallLease lease = ownership.leaseFor(definition);
        List<MarketDtos.Shop> publicShops = new ArrayList<>();
        for (Shop shop : stallShops) publicShops.add(captureShop(shop));
        publicShops.sort(java.util.Comparator.comparingInt(MarketDtos.Shop::id));
        return new MarketDtos.Stall(definition.id(), definition.buildingId(), definition.floor(), definition.publicLocation(),
                lease.owner(), lease.ownerSince(), lease.nextRentAt(), List.copyOf(lease.members()), List.copyOf(publicShops));
    }

    private MarketDtos.Shop captureShop(Shop shop) throws SQLException {
        String ownerName = shops.cachedOwnerNameIfKnown(shop.owner());
        if (ownerName == null || ownerName.isBlank()) ownerName = "Unknown player";
        int stock = shops.currentStockCount(shop);
        int trades = stock / Math.max(1, shop.sell().getAmount());
        return new MarketDtos.Shop(outbox.publicShopId(shop.id()), new MarketDtos.Identity(shop.owner().toString(), ownerName), "SELL",
                items.serialize(shop.sell()), items.serialize(shop.cost()),
                new MarketDtos.Interaction(shop.sign().world, shop.sign().x, shop.sign().y, shop.sign().z, "SHOP_INTERACTION"),
                stock, trades, shop.isSearchEnabled());
    }
}
