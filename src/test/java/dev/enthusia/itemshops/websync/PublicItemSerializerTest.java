package dev.enthusia.itemshops.websync;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PublicItemSerializerTest {
    private final PublicItemSerializer serializer = new PublicItemSerializer();

    @Test
    void serializesStandardItemCustomNameAndEnchantments() {
        MockBukkit.mock();
        try {
            ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
            var meta = item.getItemMeta();
            meta.displayName(Component.text("Market Blade"));
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            item.setItemMeta(meta);
            MarketDtos.Item result = serializer.serialize(item);
            assertEquals("DIAMOND_SWORD", result.material());
            assertEquals("Market Blade", result.metadata().get("customName"));
            assertTrue(result.metadata().containsKey("enchantments"));
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void writtenBookNeverExportsPageTextOrPdc() {
        MockBukkit.mock();
        try {
            ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
            BookMeta meta = (BookMeta) book.getItemMeta();
            meta.setTitle("Public title"); meta.setAuthor("P2wn"); meta.addPage("private page contents");
            meta.getPersistentDataContainer().set(new NamespacedKey("test", "private"), PersistentDataType.STRING, "private-pdc-value");
            book.setItemMeta(meta);
            String json = MarketJson.encode(serializer.serialize(book));
            assertTrue(json.contains("Public title"));
            assertFalse(json.contains("private page contents"));
            assertFalse(json.contains("private-pdc-value"));
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void rejectsInvalidAmounts() {
        ItemStack invalid = new ItemStack(Material.DIAMOND, 65);
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(invalid));
    }

    @Test
    void serializesStoredEnchantmentsPotionAndArmorTrim() {
        MockBukkit.mock();
        try {
            ItemStack enchantedBook = new ItemStack(Material.ENCHANTED_BOOK);
            assumeTrue(enchantedBook.getItemMeta() instanceof EnchantmentStorageMeta,
                    "MockBukkit does not implement specialized item metadata in this version");
            EnchantmentStorageMeta stored = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
            stored.addStoredEnchant(Enchantment.MENDING, 1, true);
            enchantedBook.setItemMeta(stored);
            assertTrue(serializer.serialize(enchantedBook).metadata().containsKey("storedEnchantments"));

            ItemStack potion = new ItemStack(Material.POTION);
            PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
            potionMeta.setBasePotionType(PotionType.STRONG_HEALING);
            potion.setItemMeta(potionMeta);
            assertTrue(serializer.serialize(potion).metadata().containsKey("potion"));

            ItemStack chestplate = new ItemStack(Material.DIAMOND_CHESTPLATE);
            ArmorMeta armor = (ArmorMeta) chestplate.getItemMeta();
            armor.setTrim(new ArmorTrim(TrimMaterial.GOLD, TrimPattern.SPIRE));
            chestplate.setItemMeta(armor);
            assertTrue(serializer.serialize(chestplate).metadata().containsKey("armorTrim"));
        } finally { MockBukkit.unmock(); }
    }

    @Test
    void strictBackendMetadataOmitsUnsupportedAndPrivateComponents() {
        MockBukkit.mock();
        try {
            ItemStack horn = new ItemStack(Material.GOAT_HORN);
            String json = MarketJson.encode(serializer.serialize(horn));
            assertFalse(json.contains("goatHornInstrument"));
            assertFalse(json.contains("persistentDataContainer"));
            assertFalse(json.contains("nbt"));
        } finally { MockBukkit.unmock(); }
    }
}
