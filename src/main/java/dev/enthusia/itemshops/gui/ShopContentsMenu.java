package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ShopContentsMenu implements ShopMenu {
    private final ItemShopsPlugin plugin;
    private final Player viewer;
    private final Shop shop;
    private final boolean bedrockViewer;
    private final Inventory inv;

    public ShopContentsMenu(ItemShopsPlugin plugin, Player viewer, Shop shop) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.shop = shop;
        this.bedrockViewer = Bedrock.isBedrock(viewer);

        String title = ItemUtils.colored("&8Shop Contents (read-only)");
        int size = 54;
        this.inv = Bukkit.createInventory(this, size, title);
        render();
    }

    private void render() {
        if (!bedrockViewer) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
            for (int i=0;i<inv.getSize();i++) inv.setItem(i, glass);
        } else {
            for (int i=0;i<inv.getSize();i++) inv.setItem(i, null);
        }

        Block block = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
        if (block != null && block.getState() instanceof Container cont) {
            ItemStack[] contents = cont.getInventory().getContents();
            int max = Math.min(54, contents.length);
            for (int i=0;i<max;i++) {
                inv.setItem(i, contents[i]);
            }
        }
    }

    public void open(){ viewer.openInventory(inv); }
    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return viewer; }

    @Override public boolean onClick(InventoryClickEvent e){ if (e.getView().getTopInventory().equals(inv)) e.setCancelled(true); return true; }
    @Override public boolean onDrag(InventoryDragEvent e){ if (e.getView().getTopInventory().equals(inv)) e.setCancelled(true); return true; }
    @Override public void onClose(InventoryCloseEvent e){ }
}
