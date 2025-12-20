package com.lincoln.itemshops.gui;

import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.Bedrock;
import com.lincoln.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class ShulkerPreviewMenu implements ShopMenu {
    private static final int SIZE  = 54;
    private static final int CLOSE = 49;

    private final Player viewer;
    private final Shop shop;
    private final boolean bedrockViewer;
    private final Inventory inv;

    public ShulkerPreviewMenu(Player viewer, Shop shop) {
        this.viewer = viewer; this.shop = shop;
        this.bedrockViewer = Bedrock.isBedrock(viewer);

        String title = ItemUtils.colored("&6Shulker Preview");
        this.inv = Bukkit.createInventory(this, SIZE, title);
        render();
    }

    private ItemStack pane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        return glass;
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta(); im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im);
        return i;
    }

    private void render(){
        
        if (!bedrockViewer) {
            ItemStack glass = pane();
            for (int i=0;i<SIZE;i++) inv.setItem(i, glass);
        } else {
            for (int i=0;i<SIZE;i++) inv.setItem(i, null);
        }

        
        List<ItemStack> contents = ItemUtils.getShulkerContents(shop.sell());
        int max = Math.min(27, contents.size());
        for (int i=0; i<max; i++){
            inv.setItem(9 + i, contents.get(i)); 
        }

        inv.setItem(CLOSE, named(Material.BARRIER, "&cClose", List.of()));

        
        if (!bedrockViewer) {
            ItemStack glass = pane();
            for (int i=SIZE-9;i<SIZE;i++) if (inv.getItem(i)==null) inv.setItem(i, glass);
        }
    }

    public void open(){ viewer.openInventory(inv); }

    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return viewer; }

    @Override public boolean onClick(InventoryClickEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        int raw = e.getRawSlot(); boolean top = raw < inv.getSize();
        if (!top) return false;
        e.setCancelled(true);
        if (raw == CLOSE) { viewer.closeInventory(); return true; }
        return true;
    }

    @Override public boolean onDrag(InventoryDragEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        e.setCancelled(true); return true;
    }

    @Override public void onClose(InventoryCloseEvent e) { }
}
