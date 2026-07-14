package dev.enthusia.itemshops.websync;

import java.util.List;
import java.util.Map;

public final class MarketDtos {
    private MarketDtos() {}

    public record Location(String world, int x, int y, int z) {}
    public record Avatar(String kind, String source, Boolean includesOuterLayer, String url) {}
    public record Owner(String type, String id, String uuid, String name, String avatarUrl, Avatar avatar) {}
    public record Identity(String id, String name) {}
    public record Enchantment(String id, String displayName, int level) {}
    public record PotionEffect(String name, int amplifier, int durationSeconds) {}
    public record Potion(String id, String basePotion, String form, String color, List<PotionEffect> effects) {}
    public record ArmorTrim(String pattern, String material) {}
    public record SmithingTemplate(String type) {}
    public record WrittenBook(String title, String author, String generation, int pageCount) {}
    public record ContainerEntry(Integer slot, Item item) {}
    public record Container(String type, Integer slots, Integer capacityUsed, Integer capacityMax,
                            List<ContainerEntry> contents) {}
    public record Item(String material, String displayName, int amount, String icon, Map<String, Object> metadata) {}
    public record Interaction(String world, int x, int y, int z, String source) {}
    public record Shop(int id, Identity owner, String direction, Item sellItem, Item costItem,
                       Interaction interaction, int stockCount, int availableTrades, boolean searchable) {}
    public record Stall(String id, String buildingId, int floor, Location location, Owner owner,
                        String ownerSince, String nextRentAt, List<String> members, List<Shop> shops) {}
    public record StallRevision(long revision, Stall stall) {}
}
