package com.lincoln.itemshops.gui;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.vault.VaultManager;
import com.lincoln.itemshops.vault.VaultManager.VaultEntry;
import com.lincoln.itemshops.util.Bedrock;
import com.lincoln.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class VaultMenu implements ShopMenu {
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int PREV = 45, CLOSE = 48, REDEEM_ALL = 50, NEXT = 53;

    private final ItemShopsPlugin plugin;
    private final Player player;
    private final VaultManager vm;

    private final boolean bedrockViewer;

    private Inventory inv;
    private int page = 0;
    private List<VaultEntry> entries;

    public VaultMenu(ItemShopsPlugin plugin, Player player) {
        this.plugin = plugin; this.player = player; this.vm = plugin.vault();
        this.bedrockViewer = Bedrock.isBedrock(player);

        String title = ItemUtils.colored("&6Profits Vault");
        this.inv = Bukkit.createInventory(this, SIZE, title);
        reload();
    }

    public void open(){ player.openInventory(inv); }
    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return player; }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m); ItemMeta im = i.getItemMeta(); im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im); return i;
    }

    private void reload() {
        vm.purgeExpired(player.getUniqueId());
        entries = vm.list(player.getUniqueId());
        inv.clear();
        int start = page*PER_PAGE, end = Math.min(entries.size(), start+PER_PAGE);

        for (int i=start, slot=0;i<end;i++,slot++) {
            VaultEntry ve = entries.get(i);
            ItemStack icon = ve.item.clone();
            ItemMeta im = icon.getItemMeta();
            Duration left = Duration.between(Instant.now(), Instant.ofEpochSecond(ve.expiresAt));
            long hours = Math.max(0, left.toHours());
            im.setDisplayName(ItemUtils.colored("&e" + ve.item.getType().name()));
            im.setLore(List.of(
                    ItemUtils.colored("&7Amount: &f" + ve.amount),
                    ItemUtils.colored("&7Expires in: &f" + hours + "h"),
                    ItemUtils.colored("&8Left-click: Redeem stack"),
                    ItemUtils.colored("&8Right-click: Redeem all of this entry")
            ));
            icon.setItemMeta(im);
            inv.setItem(slot, icon);
        }

        if (page>0) inv.setItem(PREV, named(Material.ARROW, "◀ &aPrev", List.of()));
        inv.setItem(CLOSE, named(Material.BARRIER, "&cClose", List.of()));
        inv.setItem(REDEEM_ALL, named(Material.EMERALD_BLOCK, "&aRedeem All", List.of("&7Redeem everything that fits")));
        if (end < entries.size()) inv.setItem(NEXT, named(Material.ARROW, "&aNext ▶", List.of()));

        
        if (!bedrockViewer) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
            for (int i=SIZE-9;i<SIZE;i++) if (inv.getItem(i)==null) inv.setItem(i, pane);
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        int raw = e.getRawSlot(); boolean top = raw < inv.getSize();
        if (!top) { e.setCancelled(true); return true; }

        e.setCancelled(true);

        if (raw == CLOSE) { player.closeInventory(); return true; }
        if (raw == PREV && page>0) { page--; reload(); return true; }
        if (raw == NEXT && (page+1)*PER_PAGE < entries.size()) { page++; reload(); return true; }
        if (raw == REDEEM_ALL) {
            vm.withdrawAll(player.getUniqueId(), player);
            reload();
            return true;
        }

        if (raw >= 0 && raw < PER_PAGE) {
            int idx = page*PER_PAGE + raw;
            if (idx >= entries.size()) return true;
            VaultEntry ve = entries.get(idx);

            if (e.getClick().isRightClick()) {
                
                int toGive = Math.min(ve.amount, ItemUtils.capacityTrades(player.getInventory(), ve.item, ve.amount));
                if (toGive > 0) {
                    ItemUtils.addExact(player.getInventory(), ve.item, toGive);
                    vm.withdraw(player.getUniqueId(), idx, toGive);
                    reload();
                }
                return true;
            } else {
                
                int stack = ve.item.getMaxStackSize();
                int toGive = Math.min(stack, ve.amount);
                toGive = Math.min(toGive, ItemUtils.capacityTrades(player.getInventory(), ve.item, toGive));
                if (toGive > 0) {
                    ItemUtils.addExact(player.getInventory(), ve.item, toGive);
                    vm.withdraw(player.getUniqueId(), idx, toGive);
                    reload();
                }
                return true;
            }
        }
        return true;
    }

    @Override public boolean onDrag(InventoryDragEvent e){ if (e.getView().getTopInventory().equals(inv)) e.setCancelled(true); return true; }
    @Override public void onClose(InventoryCloseEvent e) { }
}
