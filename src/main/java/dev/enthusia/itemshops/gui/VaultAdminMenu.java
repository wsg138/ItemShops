package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.vault.VaultManager;
import dev.enthusia.itemshops.vault.VaultManager.VaultEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VaultAdminMenu implements ShopMenu {
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int PREV = 45, CLOSE = 49, NEXT = 53;

    private final ItemShopsPlugin plugin;
    private final Player viewer;
    private final UUID target;
    private final VaultManager vm;
    private final boolean bedrockViewer;

    private Inventory inv;
    private int page = 0;
    private List<VaultEntry> entries;

    public VaultAdminMenu(ItemShopsPlugin plugin, Player viewer, UUID target) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.target = target;
        this.vm = plugin.vault();
        this.bedrockViewer = Bedrock.isBedrock(viewer);

        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null) name = "Unknown";
        String title = ItemUtils.colored("&6Vault: &e" + name);
        this.inv = Bukkit.createInventory(this, SIZE, title);
        reload();
    }

    public void open(){ viewer.openInventory(inv); }
    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return viewer; }

    private ItemStack named(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta(); im.setDisplayName(ItemUtils.colored(name));
        i.setItemMeta(im); return i;
    }

    private void reload() {
        vm.purgeExpired(target);
        entries = vm.list(target);
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
                    ItemUtils.colored("&7Expires in: &f" + hours + "h")
            ));
            icon.setItemMeta(im);
            inv.setItem(slot, icon);
        }

        if (page>0) inv.setItem(PREV, named(Material.ARROW, "◀ &aPrev"));
        inv.setItem(CLOSE, named(Material.BARRIER, "&cClose"));
        if (end < entries.size()) inv.setItem(NEXT, named(Material.ARROW, "&aNext ▶"));

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

        if (raw == CLOSE) { viewer.closeInventory(); return true; }
        if (raw == PREV && page>0) { page--; reload(); return true; }
        if (raw == NEXT && (page+1)*PER_PAGE < entries.size()) { page++; reload(); return true; }
        return true;
    }

    @Override public boolean onDrag(InventoryDragEvent e){ if (e.getView().getTopInventory().equals(inv)) e.setCancelled(true); return true; }
    @Override public void onClose(InventoryCloseEvent e) { }
}
