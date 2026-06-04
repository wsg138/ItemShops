package dev.enthusia.itemshops.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class ItemStackCodec {
    private ItemStackCodec() {
    }

    public static String encode(ItemStack item) {
        if (item == null) {
            return "";
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item.clone());
        return yaml.saveToString();
    }

    public static ItemStack decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
            ItemStack item = yaml.getItemStack("item");
            return item == null ? null : item.clone();
        } catch (Exception exception) {
            return null;
        }
    }
}
