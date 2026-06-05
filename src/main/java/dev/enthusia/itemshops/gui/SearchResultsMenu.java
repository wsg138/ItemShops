package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class SearchResultsMenu implements ShopMenu {
    private static final int SIZE = 54; 
    private static final int NEXT_SLOT = 53;
    private static final int PREV_SLOT = 45;
    private static final int CLOSE_SLOT = 49; 
    private static final int HELP_SLOT  = 47;
    private static final int PER_PAGE   = 45;

    private final ItemShopsPlugin plugin;
    private final Player viewer;
    private final List<Shop> results;
    private final String mode;
    private int page;

    
    private final boolean bedrockViewer;

    private final Inventory inv;

    public SearchResultsMenu(ItemShopsPlugin plugin, Player viewer, List<Shop> results, String queryDesc, String mode) {
        this.plugin = plugin; this.viewer = viewer; this.results = results; this.mode = mode;

        this.bedrockViewer = Bedrock.isBedrock(viewer);
        String titleTpl = plugin.getConfig().getString("gui.search-title","&6Shops for &e{query} &6({mode})");
        String title = titleTpl.replace("{query}", queryDesc).replace("{mode}", mode);
        this.inv = Bukkit.createInventory(this, SIZE, ItemUtils.colored(title));
        render();
    }

    @Override public Inventory getInventory() { return inv; }
    @Override public HumanEntity viewer() { return viewer; }
    public void open(){ viewer.openInventory(inv); }

    private ItemStack navButton(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta(); im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im);
        return i;
    }

    private void render() {
        inv.clear();
        int start = page * PER_PAGE;
        int end = Math.min(results.size(), start + PER_PAGE);

        for (int i = start, slot = 0; i < end; i++, slot++) {
            Shop s = results.get(i);
            ItemStack icon = s.sell().clone(); 
            ItemMeta im = icon.getItemMeta();

            String owner = plugin.shops().cachedOwnerName(s.owner());
            if (owner == null) owner = "Unknown";

            int trades = plugin.shops().cachedTradesAvailable(s);
            boolean inStock = trades > 0;

            im.setDisplayName(ItemUtils.colored((inStock ? "&a" : "&c") + (inStock ? "IN STOCK" : "OUT OF STOCK") + " &7- " + owner));
            List<String> lore = new ArrayList<>();
            lore.add(ItemUtils.colored("&7Location: &f" + s.sign().world + " &7" + s.sign().x + "," + s.sign().y + "," + s.sign().z));
            lore.add(ItemUtils.colored("&7Trade: &a" + s.sell().getAmount() + "x " + ItemUtils.niceName(s.sell())
                    + " &7for &e" + s.cost().getAmount() + "x " + ItemUtils.niceName(s.cost())));
            lore.add(ItemUtils.colored("&7Trades available now: &f" + trades));

            
            if (ItemUtils.isShulker(s.sell())) {
                lore.add(ItemUtils.colored("&bContains:"));
                for (String line : ItemUtils.summarizeContents(s.sell(), 5)) {
                    lore.add(ItemUtils.colored(line));
                }
                lore.add(ItemUtils.colored("&8Right-click: preview (read-only)"));
            } else {
                lore.add(ItemUtils.colored("&8Right-click: paste coords in chat"));
            }

            im.setLore(lore);
            icon.setItemMeta(im);
            inv.setItem(slot, icon);
        }

        if (end < results.size()) inv.setItem(NEXT_SLOT, navButton(Material.ARROW, "&aNext ▶", List.of()));
        if (page > 0)              inv.setItem(PREV_SLOT, navButton(Material.ARROW, "◀ &aPrev", List.of()));
        inv.setItem(CLOSE_SLOT, navButton(Material.BARRIER, "&cClose", List.of()));
        inv.setItem(HELP_SLOT,  navButton(Material.BOOK, "&7Hint",
                List.of(ItemUtils.colored("&8Right-click shulkers to preview"))));

        
        if (!bedrockViewer) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
            for (int s = SIZE - 9; s < SIZE; s++) if (inv.getItem(s) == null) inv.setItem(s, pane);
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        int raw = e.getRawSlot();
        if (raw >= inv.getSize()) return false;
        e.setCancelled(true);
        if (handleNavigation(raw)) {
            return true;
        }
        return handleResultClick(e, raw);
    }

    private boolean handleNavigation(int raw) {
        if (raw == NEXT_SLOT && (page + 1) * PER_PAGE < results.size()) {
            page++;
            render();
            return true;
        }
        if (raw == PREV_SLOT && page > 0) {
            page--;
            render();
            return true;
        }
        if (raw == CLOSE_SLOT) {
            viewer.closeInventory();
            return true;
        }
        return false;
    }

    private boolean handleResultClick(InventoryClickEvent e, int raw) {
        if (raw < 0 || raw >= PER_PAGE || !e.isRightClick()) {
            return true;
        }
        int idx = page * PER_PAGE + raw;
        if (idx >= results.size()) {
            return true;
        }
        Shop shop = results.get(idx);
        if (ItemUtils.isShulker(shop.sell())) {
            new ShulkerPreviewMenu(viewer, shop).open();
            return true;
        }
        String coords = shop.sign().world + " " + shop.sign().x + " " + shop.sign().y + " " + shop.sign().z;
        viewer.sendMessage(ItemUtils.colored("&6[ItemShops]&r Coords: &f" + coords));
        return true;
    }

    @Override
    public boolean onDrag(InventoryDragEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        for (int s : e.getRawSlots()) if (s < inv.getSize()) { e.setCancelled(true); return true; }
        return true;
    }

    @Override
    public void onClose(InventoryCloseEvent e) { }
}
