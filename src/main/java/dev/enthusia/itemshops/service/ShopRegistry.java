package dev.enthusia.itemshops.service;

import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Pos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopRegistry {
    private final Map<Pos, List<Shop>> shopsByContainer = new ConcurrentHashMap<>();
    private final Map<Pos, Shop> shopsBySign = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> ownedCounts = new ConcurrentHashMap<>();

    public void clear() {
        shopsByContainer.clear();
        shopsBySign.clear();
        ownedCounts.clear();
    }

    public int size() {
        int count = 0;
        for (List<Shop> shops : shopsByContainer.values()) {
            count += shops.size();
        }
        return count;
    }

    public boolean hasAnyShops() {
        return !shopsBySign.isEmpty();
    }

    public Collection<Shop> all() {
        List<Shop> out = new ArrayList<>();
        for (List<Shop> shops : shopsByContainer.values()) {
            out.addAll(shops);
        }
        return out;
    }

    public Shop getBySign(Pos signPos) {
        return shopsBySign.get(signPos);
    }

    public boolean hasShopAtSign(Pos signPos) {
        return shopsBySign.containsKey(signPos);
    }

    public List<Shop> shopsOn(Pos containerPos) {
        List<Shop> list = shopsByContainer.get(containerPos);
        if (list == null || list.isEmpty()) return List.of();
        return new ArrayList<>(list);
    }

    public boolean hasShopsOnContainer(Pos containerPos) {
        List<Shop> list = shopsByContainer.get(containerPos);
        return list != null && !list.isEmpty();
    }

    public List<Shop> shopsOnFast(Pos containerPos) {
        List<Shop> list = shopsByContainer.get(containerPos);
        return list == null || list.isEmpty() ? List.of() : list;
    }

    public List<Shop> ownedBy(UUID ownerId) {
        return all().stream().filter(shop -> shop.owner().equals(ownerId)).toList();
    }

    public int ownedCount(UUID ownerId) {
        return ownedCounts.getOrDefault(ownerId, 0);
    }

    public void put(Shop shop) {
        List<Shop> shops = shopsByContainer.computeIfAbsent(shop.container(), ignored -> new ArrayList<>());
        boolean added = false;
        if (!shops.contains(shop)) {
            shops.add(shop);
            added = true;
        }

        Shop previous = shopsBySign.put(shop.sign(), shop);
        if (previous != null && previous != shop) {
            remove(previous);
        }
        if (added) {
            incOwner(shop.owner());
        }
    }

    public void indexExisting(Shop shop) {
        shopsByContainer.computeIfAbsent(shop.container(), ignored -> new ArrayList<>()).add(shop);
        shopsBySign.put(shop.sign(), shop);
        incOwner(shop.owner());
    }

    public void relinkSign(Pos oldSign, Pos newSign, Shop shop) {
        if (oldSign != null) {
            shopsBySign.remove(oldSign);
        }
        shop.setSign(newSign);
        shopsBySign.put(newSign, shop);
    }

    public boolean remove(Shop shop) {
        List<Shop> shops = shopsByContainer.get(shop.container());
        if (shops == null) return false;
        boolean removed = shops.remove(shop);
        if (!removed) return false;

        if (shops.isEmpty()) {
            shopsByContainer.remove(shop.container());
        }
        shopsBySign.remove(shop.sign(), shop);
        decOwner(shop.owner());
        return true;
    }

    private void incOwner(UUID ownerId) {
        ownedCounts.merge(ownerId, 1, Integer::sum);
    }

    private void decOwner(UUID ownerId) {
        ownedCounts.compute(ownerId, (ignored, current) -> {
            int next = (current == null ? 0 : current) - 1;
            return next > 0 ? next : null;
        });
    }
}
