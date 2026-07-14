package dev.enthusia.itemshops.websync;

import java.util.List;

public interface StallOwnershipProvider {
    boolean available();
    String name();
    String unavailableReason();
    StallLease leaseFor(CanonicalMarketLayout.StallDefinition stall);

    record StallLease(boolean available, MarketDtos.Owner owner, String ownerSince, String nextRentAt,
                      List<String> members, String authoritativeRegionId) {}
}
