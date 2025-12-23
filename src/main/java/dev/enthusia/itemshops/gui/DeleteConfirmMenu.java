package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Texts;
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

import java.util.List;

public final class DeleteConfirmMenu implements ShopMenu {
    private static final int SIZE = 54;
    private static final int SELL_SLOT = 20;
    private static final int COST_SLOT = 24;
    private static final int INFO_SLOT = 31;
    private static final int CONFIRM_SLOT = 47;
    private static final int BACK_SLOT = 51;

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player viewer;
    private final Shop shop;
    private final boolean showAll;
    private final int returnPage;
    private final boolean bedrockViewer;
    private final Inventory inv;

    public DeleteConfirmMenu(ItemShopsPlugin plugin, ShopManager mgr, Player viewer, Shop shop, boolean showAll, int returnPage) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.viewer = viewer;
        this.shop = shop;
        this.showAll = showAll;
        this.returnPage = returnPage;
        this.bedrockViewer = Bedrock.isBedrock(viewer);

        String title = ItemUtils.colored("&cConfirm Delete");
        this.inv = Bukkit.createInventory(this, SIZE, title);
        render();
    }

    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return viewer; }
    public void open(){ viewer.openInventory(inv); }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im);
        return i;
    }

    private void render() {
        if (!bedrockViewer) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
            for (int i=0;i<SIZE;i++) inv.setItem(i, glass);
        } else {
            for (int i=0;i<SIZE;i++) inv.setItem(i, null);
        }

        ItemStack sell = shop.sell().clone();
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(ItemUtils.colored("&aYOU SELL"));
        sm.setLore(List.of(ItemUtils.colored("&7Per trade")));
        sell.setItemMeta(sm);
        inv.setItem(SELL_SLOT, sell);

        ItemStack cost = shop.cost().clone();
        ItemMeta cm = cost.getItemMeta();
        cm.setDisplayName(ItemUtils.colored("&eYOU GET"));
        cm.setLore(List.of(ItemUtils.colored("&7Per trade")));
        cost.setItemMeta(cm);
        inv.setItem(COST_SLOT, cost);

        String owner = Bukkit.getOfflinePlayer(shop.owner()).getName();
        if (owner == null) owner = "Unknown";
        ItemStack info = named(Material.PAPER, "&cThis will delete the shop", List.of(
                "&7Owner: &f" + owner,
                "&7Location: &f" + shop.sign().world + " " + shop.sign().x + "," + shop.sign().y + "," + shop.sign().z
        ));
        inv.setItem(INFO_SLOT, info);

        inv.setItem(CONFIRM_SLOT, named(Material.RED_CONCRETE, "&cConfirm Delete", List.of("&7This cannot be undone")));
        inv.setItem(BACK_SLOT, named(Material.BARRIER, "&7Back", List.of("&7Return to the list")));

        if (!bedrockViewer) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
            for (int i=SIZE-9;i<SIZE;i++) if (inv.getItem(i)==null) inv.setItem(i, glass);
        }
    }

    @Override
    public boolean onClick(InventoryClickEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        int raw = e.getRawSlot(); boolean top = raw < inv.getSize();
        if (!top) return false;
        e.setCancelled(true);

        if (raw == BACK_SLOT) {
            new DeleteShopsMenu(plugin, mgr, viewer, showAll, returnPage).open();
            return true;
        }

        if (raw == CONFIRM_SLOT) {
            boolean canDelete = viewer.hasPermission("itemshops.admin") || shop.owner().equals(viewer.getUniqueId());
            if (!canDelete) {
                viewer.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
                new DeleteShopsMenu(plugin, mgr, viewer, showAll, returnPage).open();
                return true;
            }
            mgr.deleteShop(shop, ShopManager.RemovalReason.ADMIN_COMMAND, viewer.getUniqueId());
            viewer.sendMessage(ItemUtils.colored("&eShop deleted."));
            new DeleteShopsMenu(plugin, mgr, viewer, showAll, returnPage).open();
            return true;
        }
        return true;
    }

    @Override public boolean onDrag(InventoryDragEvent e){ if (e.getView().getTopInventory().equals(inv)) e.setCancelled(true); return true; }
    @Override public void onClose(InventoryCloseEvent e){ }
}
