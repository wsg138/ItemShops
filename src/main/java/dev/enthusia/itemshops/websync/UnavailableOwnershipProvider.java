package dev.enthusia.itemshops.websync;

public final class UnavailableOwnershipProvider implements StallOwnershipProvider {
    public static final String REQUIRED_API = "A deployed AdvancedRegionMarket-compatible provider exposing region identity, sold state, owner/members, rent expiry, and ARM-Guilds-Bridge guild ownership";

    @Override public boolean available() { return false; }
    @Override public String name() { return "Unavailable"; }
    @Override public String unavailableReason() { return REQUIRED_API; }
    @Override public StallLease leaseFor(CanonicalMarketLayout.StallDefinition stall) {
        throw new IllegalStateException(unavailableReason());
    }
}
