package com.lincoln.itemshops.gui;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;


public interface ShopMenu extends InventoryHolder {
    @Override Inventory getInventory();

    
    HumanEntity viewer();

    
    default void open() { if (viewer() != null) viewer().openInventory(getInventory()); }

    
    default boolean onClick(InventoryClickEvent e) { return false; }

    
    default boolean onDrag(InventoryDragEvent e) { return false; }

    
    default void onClose(InventoryCloseEvent e) {}
}
