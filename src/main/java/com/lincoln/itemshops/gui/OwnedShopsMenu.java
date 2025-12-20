package com.lincoln.itemshops.gui;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.Bedrock;
import com.lincoln.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class OwnedShopsMenu implements ShopMenu {
    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player owner;
    private List<Shop> shops;
    private final Inventory inv;

    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int PREV = 45, CLOSE = 49, NEXT = 53;

    private final boolean bedrockViewer;

    private int page = 0;

    public OwnedShopsMenu(ItemShopsPlugin plugin, ShopManager mgr, Player owner) {
        this.plugin = plugin; this.mgr = mgr; this.owner = owner;
        this.bedrockViewer = Bedrock.isBedrock(owner);

        this.shops = mgr.ownedBy(owner.getUniqueId());
        String title = ItemUtils.colored("&bYour Shops (click to edit)");
        this.inv = Bukkit.createInventory(this, SIZE, title);
        render();
    }

    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return owner; }
    public void open(){ owner.openInventory(inv); }

    private ItemStack named(Material m, String name) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.setDisplayName(ItemUtils.colored(name));
        i.setItemMeta(im);
        return i;
    }

    private void render(){
        mgr.pruneInvalidShops();
        shops = mgr.ownedBy(owner.getUniqueId());

        inv.clear();
        int start = page*PER_PAGE, end = Math.min(shops.size(), start+PER_PAGE);
        for (int i=start, slot=0;i<end;i++,slot++){
            Shop s = shops.get(i);
            ItemStack icon = s.sell().clone();
            ItemMeta im = icon.getItemMeta();
            im.setDisplayName(ItemUtils.colored("&f" + s.sign().world + " " + s.sign().x + "," + s.sign().y + "," + s.sign().z));
            im.setLore(java.util.List.of(
                    ItemUtils.colored("&7Trade: &a"+s.sell().getAmount()+"x "+ItemUtils.niceName(s.sell())+
                            " &7for &e"+s.cost().getAmount()+"x "+ItemUtils.niceName(s.cost())),
                    ItemUtils.colored("&8Click to edit")
            ));
            icon.setItemMeta(im);
            inv.setItem(slot, icon);
        }

        
        inv.setItem(CLOSE, named(Material.BARRIER, "&cClose"));
        if (page>0) inv.setItem(PREV, named(Material.ARROW, "◀ &aPrev"));
        if (end < shops.size()) inv.setItem(NEXT, named(Material.ARROW, "&aNext ▶"));

        
        if (!bedrockViewer) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
            for (int i=SIZE-9;i<SIZE;i++) if (inv.getItem(i)==null) inv.setItem(i, pane);
        }
    }

    @Override public boolean onClick(InventoryClickEvent e){
        int raw = e.getRawSlot(); boolean top = raw < inv.getSize();

        if (top) {
            e.setCancelled(true);
            if (raw == CLOSE) { owner.closeInventory(); return true; }
            if (raw == PREV && page>0) { page--; render(); return true; }
            if (raw == NEXT && (page+1)*PER_PAGE < shops.size()) { page++; render(); return true; }
            if (raw >=0 && raw < PER_PAGE && inv.getItem(raw)!=null) {
                int idx = page*PER_PAGE + raw; if (idx >= shops.size()) return true;
                new ShopEditMenu(plugin, mgr, owner, shops.get(idx)).open();
                return true;
            }
            return true;
        } else {
            if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || e.getAction() == InventoryAction.HOTBAR_SWAP
                    || e.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD
                    || e.getClick()  == ClickType.NUMBER_KEY) {
                e.setCancelled(true);
                return true;
            }
            return false;
        }
    }

    @Override public boolean onDrag(InventoryDragEvent e){ e.setCancelled(true); return true; }
    @Override public void onClose(InventoryCloseEvent e){ }
}
