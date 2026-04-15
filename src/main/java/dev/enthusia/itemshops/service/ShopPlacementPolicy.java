package dev.enthusia.itemshops.service;

import dev.enthusia.itemshops.config.ItemShopsConfig;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.region.MarketRegionManager;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public final class ShopPlacementPolicy {
    private final ItemShopsConfig config;
    private final MarketRegionManager marketRegionManager;
    private final ShopRegistry registry;

    public ShopPlacementPolicy(ItemShopsConfig config, MarketRegionManager marketRegionManager, ShopRegistry registry) {
        this.config = config;
        this.marketRegionManager = marketRegionManager;
        this.registry = registry;
    }

    public boolean canCreate(Player player) {
        if (player.hasPermission("itemshops.maxshops.bypass")) return true;
        return registry.ownedCount(player.getUniqueId()) < config.maxShopsPerPlayer();
    }

    public String validatePlacement(Player player, Pos containerPos) {
        List<Shop> onThis = registry.shopsOn(containerPos);
        if (!onThis.isEmpty()) {
            boolean otherOwner = onThis.stream().anyMatch(shop -> !shop.owner().equals(player.getUniqueId()));
            if (config.oneOwnerPerContainer() && otherOwner) {
                return "errors.container-claimed";
            }
        }
        if (onThis.size() >= config.maxShopsPerContainer()) {
            return "errors.container-shop-limit";
        }
        return validateSpacing(containerPos);
    }

    public String validateSpacing(Pos containerPos) {
        if (marketRegionManager.isInMarket(containerPos)) {
            return null;
        }

        int radius = config.outsideSpacingRadius();
        if (radius <= 0) return null;

        Shop nearby = findAnyShopWithin(containerPos, radius);
        if (nearby != null && !nearby.container().equals(containerPos)) {
            return "errors.spacing-outside";
        }
        return null;
    }

    public boolean isInMarket(Pos containerPos) {
        return marketRegionManager.isInMarket(containerPos);
    }

    public int outsideSpacingRadius() {
        return config.outsideSpacingRadius();
    }

    public Shop findAnyShopWithin(Pos center, int radius) {
        if (radius <= 0) return null;
        for (Shop shop : registry.all()) {
            if (!Objects.equals(shop.container().world, center.world)) continue;
            int dx = Math.abs(shop.container().x - center.x);
            int dy = Math.abs(shop.container().y - center.y);
            int dz = Math.abs(shop.container().z - center.z);
            int cheb = Math.max(dx, Math.max(dy, dz));
            if (cheb <= radius) return shop;
        }
        return null;
    }
}
