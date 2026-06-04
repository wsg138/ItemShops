package dev.enthusia.itemshops.data;

import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StoredShop {
    private final UUID id;
    private final Pos sign;
    private final Pos container;
    private final UUID owner;
    private final ItemStack sell;
    private final ItemStack cost;
    private final List<UUID> trusted;
    private final boolean hopperAllowIn;
    private final boolean hopperAllowOut;
    private final boolean searchEnabled;
    private final boolean frozen;
    private final long frozenUntilMs;

    public StoredShop(UUID id,
                      Pos sign,
                      Pos container,
                      UUID owner,
                      ItemStack sell,
                      ItemStack cost,
                      List<UUID> trusted,
                      boolean hopperAllowIn,
                      boolean hopperAllowOut,
                      boolean searchEnabled,
                      boolean frozen,
                      long frozenUntilMs) {
        this.id = id;
        this.sign = sign;
        this.container = container;
        this.owner = owner;
        this.sell = sell == null ? null : sell.clone();
        this.cost = cost == null ? null : cost.clone();
        this.trusted = trusted == null ? List.of() : List.copyOf(trusted);
        this.hopperAllowIn = hopperAllowIn;
        this.hopperAllowOut = hopperAllowOut;
        this.searchEnabled = searchEnabled;
        this.frozen = frozen;
        this.frozenUntilMs = frozenUntilMs;
    }

    public static StoredShop from(Shop shop) {
        return new StoredShop(
                shop.id(),
                shop.sign(),
                shop.container(),
                shop.owner(),
                shop.sell(),
                shop.cost(),
                new ArrayList<>(shop.trusted()),
                shop.isHopperAllowIn(),
                shop.isHopperAllowOut(),
                shop.isSearchEnabled(),
                shop.isFrozen(),
                shop.frozenUntilMs()
        );
    }

    public Shop toShop() {
        if (id == null || sign == null || container == null || owner == null || sell == null || cost == null) {
            return null;
        }
        Shop shop = new Shop(id, sign, container, owner, sell.clone(), cost.clone());
        for (UUID trustedPlayer : trusted) {
            shop.addTrusted(trustedPlayer);
        }
        shop.setHopperAllowIn(hopperAllowIn);
        shop.setHopperAllowOut(hopperAllowOut);
        shop.setSearchEnabled(searchEnabled);
        if (frozen) {
            if (frozenUntilMs > 0L) {
                shop.freezeUntil(frozenUntilMs);
            } else {
                shop.freezeIndefinitely();
            }
        }
        return shop;
    }

    public UUID id() {
        return id;
    }

    public Pos sign() {
        return sign;
    }

    public Pos container() {
        return container;
    }

    public UUID owner() {
        return owner;
    }

    public ItemStack sell() {
        return sell == null ? null : sell.clone();
    }

    public ItemStack cost() {
        return cost == null ? null : cost.clone();
    }

    public List<UUID> trusted() {
        return trusted;
    }

    public boolean hopperAllowIn() {
        return hopperAllowIn;
    }

    public boolean hopperAllowOut() {
        return hopperAllowOut;
    }

    public boolean searchEnabled() {
        return searchEnabled;
    }

    public boolean frozen() {
        return frozen;
    }

    public long frozenUntilMs() {
        return frozenUntilMs;
    }
}
