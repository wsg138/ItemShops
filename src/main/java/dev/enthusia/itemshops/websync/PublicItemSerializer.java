package dev.enthusia.itemshops.websync;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PublicItemSerializer {
    public static final int MAX_DEPTH = 4;
    public static final int MAX_NESTED_ENTRIES = 1024;
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public MarketDtos.Item serialize(ItemStack stack) {
        Counter counter = new Counter();
        return serialize(stack, 0, new IdentityHashMap<>(), counter);
    }

    private MarketDtos.Item serialize(ItemStack stack, int depth, IdentityHashMap<ItemStack, Boolean> path, Counter counter) {
        if (stack == null || stack.getType().isAir()) throw new IllegalArgumentException("Public item must not be empty");
        if (stack.getAmount() <= 0 || stack.getAmount() > stack.getMaxStackSize()) throw new IllegalArgumentException("Public item amount is outside its material stack limit");
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("Public item nesting exceeds maximum depth");
        if (path.put(stack, Boolean.TRUE) != null) throw new IllegalArgumentException("Recursive public item container detected");
        if (++counter.entries > MAX_NESTED_ENTRIES) throw new IllegalArgumentException("Public item container entry limit exceeded");
        try {
            ItemMeta meta = stack.getItemMeta();
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (meta != null && meta.hasDisplayName()) metadata.put("customName", bounded(PLAIN.serialize(meta.displayName()), 256));
            if (meta != null && meta.hasEnchants()) metadata.put("enchantments", enchantments(meta.getEnchants()));
            if (meta instanceof EnchantmentStorageMeta stored && stored.hasStoredEnchants()) metadata.put("storedEnchantments", enchantments(stored.getStoredEnchants()));
            if (meta instanceof PotionMeta potion) addPotion(stack, potion, metadata);
            if (meta instanceof ArmorMeta armor && armor.hasTrim()) {
                metadata.put("armorTrim", new MarketDtos.ArmorTrim(armor.getTrim().getPattern().getKey().getKey(), armor.getTrim().getMaterial().getKey().getKey()));
            }
            if (stack.getType().name().endsWith("_SMITHING_TEMPLATE")) {
                metadata.put("smithingTemplate", new MarketDtos.SmithingTemplate(stack.getType().getKey().toString()));
            }
            if (meta instanceof BookMeta book && book.hasTitle() && book.hasAuthor()) {
                String generation = book.hasGeneration() ? book.getGeneration().name() : "ORIGINAL";
                metadata.put("writtenBook", new MarketDtos.WrittenBook(bounded(book.getTitle(), 256), bounded(book.getAuthor(), 128), generation, book.getPageCount()));
            }
            if (meta instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof ShulkerBox shulker) {
                List<MarketDtos.ContainerEntry> contents = new ArrayList<>();
                ItemStack[] inventory = shulker.getInventory().getContents();
                for (int slot = 0; slot < inventory.length; slot++) {
                    ItemStack nested = inventory[slot];
                    if (nested != null && !nested.getType().isAir()) contents.add(new MarketDtos.ContainerEntry(slot, serialize(nested, depth + 1, path, counter)));
                }
                metadata.put("shulkerColor", shulker.getColor() == null ? "UNCOLORED" : shulker.getColor().name());
                metadata.put("container", new MarketDtos.Container("SHULKER", 27, null, null, List.copyOf(contents)));
            } else if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
                List<MarketDtos.ContainerEntry> contents = new ArrayList<>();
                int capacity = 0;
                for (ItemStack nested : bundle.getItems()) {
                    if (nested == null || nested.getType().isAir()) continue;
                    capacity += nested.getAmount() * Math.max(1, 64 / nested.getMaxStackSize());
                    contents.add(new MarketDtos.ContainerEntry(null, serialize(nested, depth + 1, path, counter)));
                }
                metadata.put("container", new MarketDtos.Container("BUNDLE", null, Math.min(capacity, 1_000_000), 64, List.copyOf(contents)));
            }
            return new MarketDtos.Item(stack.getType().name(), title(stack.getType()), stack.getAmount(), null,
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(metadata)));
        } finally {
            path.remove(stack);
        }
    }

    private static List<MarketDtos.Enchantment> enchantments(Map<Enchantment, Integer> values) {
        return values.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().getKey().toString()))
                .map(entry -> new MarketDtos.Enchantment(entry.getKey().getKey().toString(), title(entry.getKey().getKey().getKey()), entry.getValue())).toList();
    }

    private static void addPotion(ItemStack stack, PotionMeta meta, Map<String, Object> metadata) {
        PotionType base = meta.hasBasePotionType() ? meta.getBasePotionType() : PotionType.WATER;
        List<PotionEffect> effects = new ArrayList<>(base.getPotionEffects());
        effects.addAll(meta.getCustomEffects());
        List<MarketDtos.PotionEffect> publicEffects = effects.stream().limit(64)
                .map(effect -> new MarketDtos.PotionEffect(title(effect.getType().getKey().getKey()), effect.getAmplifier(), Math.max(0, effect.getDuration() / 20))).toList();
        Color color = meta.hasColor() ? meta.getColor() : (!effects.isEmpty() ? effects.getFirst().getType().getColor() : Color.fromRGB(0x385DC6));
        String hex = String.format(Locale.ROOT, "#%06X", color.asRGB() & 0xFFFFFF);
        metadata.put("potion", new MarketDtos.Potion(base.getKey().toString(), title(base.getKey().getKey()), stack.getType().name(), hex, publicEffects));
    }

    static String title(Material material) { return title(material.name()); }
    static String title(String key) {
        String[] words = key.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return bounded(out.toString(), 256);
    }

    private static String bounded(String value, int length) {
        if (value == null || value.isBlank()) return "Unknown";
        return value.length() <= length ? value : value.substring(0, length);
    }

    private static final class Counter { int entries; }
}
