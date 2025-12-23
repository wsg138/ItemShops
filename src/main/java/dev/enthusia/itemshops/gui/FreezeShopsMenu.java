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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FreezeShopsMenu implements ShopMenu {
    private static final int SIZE = 54;
    private static final int PER_PAGE = 45;
    private static final int PREV = 45, CLOSE = 49, NEXT = 53;

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player viewer;
    private final UUID target;
    private final long durationMs;
    private final boolean bedrockViewer;
    private final Inventory inv;
    private List<Shop> shops;
    private int page = 0;

    public FreezeShopsMenu(ItemShopsPlugin plugin, ShopManager mgr, Player viewer, UUID target, long durationMs) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.viewer = viewer;
        this.target = target;
        this.durationMs = durationMs;
        this.bedrockViewer = Bedrock.isBedrock(viewer);

        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null) name = "Unknown";
        String title = ItemUtils.colored("&bFreeze Shops: &f" + name);
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

    private String formatDuration(long ms) {
        if (ms <= 0) return "indefinite";
        Duration d = Duration.ofMillis(ms);
        long mins = Math.max(1, d.toMinutes());
        return mins + "m";
    }

    private void render(){
        mgr.pruneInvalidShops();
        shops = mgr.ownedBy(target);

        inv.clear();
        int start = page*PER_PAGE, end = Math.min(shops.size(), start+PER_PAGE);
        for (int i=start, slot=0;i<end;i++,slot++){
            Shop s = shops.get(i);
            boolean frozen = s.isFrozen();
            ItemStack icon = s.sell().clone();
            ItemMeta im = icon.getItemMeta();
            String status = frozen ? "&cFrozen" : "&aActive";
            List<String> lore = new ArrayList<>();
            lore.add(ItemUtils.colored("&7Trade: &a"+s.sell().getAmount()+"x "+ItemUtils.niceName(s.sell())+
                    " &7for &e"+s.cost().getAmount()+"x "+ItemUtils.niceName(s.cost())));
            lore.add(ItemUtils.colored("&7Status: " + status));
            if (frozen && s.frozenUntilMs() > 0) {
                long leftMs = s.frozenUntilMs() - System.currentTimeMillis();
                if (leftMs < 0) leftMs = 0;
                lore.add(ItemUtils.colored("&7Unfreezes in: &f" + formatDuration(leftMs)));
            }
            lore.add(ItemUtils.colored("&8Click to " + (frozen ? "unfreeze" : "freeze (" + formatDuration(durationMs) + ")")));
            im.setDisplayName(ItemUtils.colored("&f" + s.sign().world + " " + s.sign().x + "," + s.sign().y + "," + s.sign().z));
            im.setLore(lore);
            icon.setItemMeta(im);
            inv.setItem(slot, icon);
        }

        inv.setItem(CLOSE, named(Material.BARRIER, "&cClose", List.of()));
        if (page>0) inv.setItem(PREV, named(Material.ARROW, "◀ &aPrev", List.of()));
        if (end < shops.size()) inv.setItem(NEXT, named(Material.ARROW, "&aNext ▶", List.of()));

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
            if (raw == CLOSE) { viewer.closeInventory(); return true; }
            if (raw == PREV && page>0) { page--; render(); return true; }
            if (raw == NEXT && (page+1)*PER_PAGE < shops.size()) { page++; render(); return true; }
            if (raw >=0 && raw < PER_PAGE && inv.getItem(raw)!=null) {
                int idx = page*PER_PAGE + raw; if (idx >= shops.size()) return true;
                Shop s = shops.get(idx);
                if (s.isFrozen()) {
                    s.unfreeze();
                    mgr.requestSave();
                    viewer.sendMessage(ItemUtils.colored("&eUnfroze shop."));
                } else {
                    if (durationMs > 0) s.freezeUntil(System.currentTimeMillis() + durationMs);
                    else s.freezeIndefinitely();
                    mgr.requestSave();
                    viewer.sendMessage(ItemUtils.colored("&eFroze shop."));
                }
                render();
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
