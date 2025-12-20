package com.lincoln.itemshops.gui;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.Bedrock;
import com.lincoln.itemshops.util.ItemUtils;
import com.lincoln.itemshops.util.Texts;
import com.lincoln.itemshops.vault.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
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

import java.util.ArrayList;
import java.util.List;

public final class PurchaseMenu implements ShopMenu {
    private static final int SIZE = 54;
    private static final int SELL_SLOT   = 20; 
    private static final int COST_SLOT   = 24; 
    private static final int STATUS_SLOT = 31;
    private static final int CANCEL_SLOT = 4;
    private static final int BUY1_SLOT   = 47;
    private static final int CUSTOM_SLOT = 49;
    private static final int MAX_SLOT    = 51;

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player buyer;
    private final Shop shop;
    private final VaultManager vault;

    private final boolean isBedrock;

    private final Inventory inv;

    public PurchaseMenu(ItemShopsPlugin plugin, ShopManager mgr, Player buyer, Shop shop) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.buyer = buyer;
        this.shop = shop;
        this.vault = plugin.vault();
        this.isBedrock = Bedrock.isBedrock(buyer);

        String baseTitle = plugin.getConfig().getString("gui.purchase-title", "Purchase from Shop");
        String title = ItemUtils.colored(baseTitle);
        this.inv = Bukkit.createInventory(this, SIZE, title);
        decorate();
    }

    @Override public Inventory getInventory() { return inv; }
    @Override public HumanEntity viewer() { return buyer; }

    private ItemStack pane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(ItemUtils.colored(plugin.getConfig().getString("gui.glass-name", " ")));
        glass.setItemMeta(gm);
        return glass;
    }

    private ItemStack label(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta();
        im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im);
        return i;
    }

    private void decorate() {
        
        if (!isBedrock) {
            ItemStack glass = pane();
            for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);
        } else {
            for (int i = 0; i < SIZE; i++) inv.setItem(i, null);
        }

        
        ItemStack sell = shop.sell().clone();
        ItemMeta sm = sell.getItemMeta();
        sm.setDisplayName(ItemUtils.colored("&aYOU RECEIVE"));
        sm.setLore(List.of(ItemUtils.colored("&7Per trade")));
        sell.setItemMeta(sm);

        ItemStack cost = shop.cost().clone();
        ItemMeta cm = cost.getItemMeta();
        cm.setDisplayName(ItemUtils.colored("&eYOU PAY"));
        cm.setLore(List.of(ItemUtils.colored("&7Per trade")));
        cost.setItemMeta(cm);

        inv.setItem(SELL_SLOT, sell);
        inv.setItem(COST_SLOT, cost);

        
        inv.setItem(CANCEL_SLOT, label(Material.BARRIER, plugin.getConfig().getString("gui.cancel-name", "Cancel"), List.of()));

        
        inv.setItem(BUY1_SLOT,   label(Material.LIME_CONCRETE,   "&aPurchase 1 Trade",       List.of("&7Buy exactly 1 trade")));
        inv.setItem(CUSTOM_SLOT, label(Material.CYAN_CONCRETE,   "&bPurchase Custom Amount", List.of("&7Type amount in chat")));
        inv.setItem(MAX_SLOT,    label(Material.YELLOW_CONCRETE, "&ePurchase Max",           List.of("&7Buy as many as possible")));

        
        refreshStatus();

        
        if (!isBedrock && SIZE >= 54) {
            ItemStack pane2 = pane();
            for (int i = 45; i < 54; i++) if (inv.getItem(i) == null) inv.setItem(i, pane2);
        }
    }

    public void open() { buyer.openInventory(inv); }

    private void refreshStatus() {
        int stockTrades = computeStockTrades();
        int canAffordTrades = computeAffordableTrades();
        ItemStack status = new ItemStack(Material.PAPER);
        ItemMeta im = status.getItemMeta();
        im.setDisplayName(ItemUtils.colored("&bStatus"));

        List<String> lore = new ArrayList<>();
        lore.add(ItemUtils.colored("&7Stock trades available: &f" + stockTrades));
        lore.add(ItemUtils.colored("&7You can afford: &f" + canAffordTrades));

        im.setLore(lore);
        status.setItemMeta(im);
        inv.setItem(STATUS_SLOT, status);
    }

    private int computeStockTrades() {
        Block contBlock = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
        if (contBlock == null || !(contBlock.getState() instanceof Container cont)) return 0;
        int stock = ItemUtils.countSimilar(cont.getInventory(), shop.sell());
        return stock / Math.max(1, shop.sell().getAmount());
    }

    private int computeAffordableTrades() {
        int have = ItemUtils.countSimilar(buyer.getInventory(), shop.cost());
        return have / Math.max(1, shop.cost().getAmount());
    }

    private int computeCapacity(Inventory inv, ItemStack template) {
        if (template == null || template.getType().isAir()) return 0;
        int max = template.getMaxStackSize();
        int addable = 0;
        for (ItemStack cur : inv.getStorageContents()) {
            if (cur == null || cur.getType() == Material.AIR) addable += max;
            else if (cur.isSimilar(template)) addable += (max - cur.getAmount());
        }
        return addable;
    }

    
    private int computeMaxTrades() {
        Block contBlock = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
        if (contBlock == null || !(contBlock.getState() instanceof Container cont)) return 0;

        Inventory contInv = cont.getInventory();
        Inventory buyerInv = buyer.getInventory();
        ItemStack sellT = shop.sell();
        ItemStack costT = shop.cost();

        int tradesByStock   = ItemUtils.countSimilar(contInv, sellT) / Math.max(1, sellT.getAmount());
        int tradesByWallet  = ItemUtils.countSimilar(buyerInv, costT) / Math.max(1, costT.getAmount());
        int capacityBuyer   = computeCapacity(buyerInv, sellT) / Math.max(1, sellT.getAmount());

        
        return Math.max(0, Math.min(Math.min(tradesByStock, tradesByWallet), capacityBuyer));
    }

    @Override
    public boolean onClick(InventoryClickEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;

        int raw = e.getRawSlot();
        boolean top = raw < inv.getSize();

        if (top) {
            e.setCancelled(true);
            if (raw == CANCEL_SLOT) { buyer.closeInventory(); return true; }

            if (raw == BUY1_SLOT)   { performPurchase(1); buyer.closeInventory(); return true; }
            if (raw == MAX_SLOT)    {
                int max = computeMaxTrades();
                if (max <= 0) { buyer.sendMessage(Texts.msg(plugin.messages(), "errors.buymax-none")); return true; }
                performPurchase(max); buyer.closeInventory(); return true;
            }
            if (raw == CUSTOM_SLOT) {
                ChatAmountCapture.request(buyer, "Enter number of trades to purchase", amount -> {
                    int max = computeMaxTrades();
                    if (max <= 0) { buyer.sendMessage(Texts.msg(plugin.messages(), "errors.buymax-none")); return; }
                    if (amount > max) amount = max;
                    performPurchase(amount);
                    buyer.closeInventory();
                });
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

    @Override
    public boolean onDrag(InventoryDragEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;
        for (int s : e.getRawSlots()) if (s < inv.getSize()) { e.setCancelled(true); return true; }
        return true;
    }

    @Override
    public void onClose(InventoryCloseEvent e) { }

    
    private void performPurchase(int trades) {
        if (trades <= 0) return;

        
        if (buyer.getUniqueId().equals(shop.owner())) {
            buyer.sendMessage(Texts.msg(plugin.messages(), "errors.self-purchase"));
            return;
        }

        var lock = shop.lock();
        lock.lock();
        try {
            Block contBlock = shop.container().toLocation() == null ? null : shop.container().toLocation().getBlock();
            if (contBlock == null || !(contBlock.getState() instanceof Container cont)) {
                buyer.sendMessage(Texts.msg(plugin.messages(), "errors.unsafe"));
                return;
            }
            Inventory contInv = cont.getInventory();
            ItemStack sellT = shop.sell(); int sellAmt = sellT.getAmount() * trades;
            ItemStack costT = shop.cost(); int costAmt = costT.getAmount() * trades;

            
            int stock = ItemUtils.countSimilar(contInv, sellT);
            if (stock < sellAmt) { buyer.sendMessage(Texts.msg(plugin.messages(), "errors.stock-empty")); return; }

            Inventory buyerInv = buyer.getInventory();
            int have = ItemUtils.countSimilar(buyerInv, costT);
            if (have < costAmt) { buyer.sendMessage(Texts.msg(plugin.messages(), "errors.payer-lacks")); return; }

            if (!ItemUtils.canFit(buyerInv, sellT, sellAmt)) { buyer.sendMessage(Texts.msg(plugin.messages(), "errors.buyer-space")); return; }

            
            var removedFromCont  = ItemUtils.removeSimilar(contInv, sellT, sellAmt);
            if (removedFromCont.isEmpty()) { buyer.sendMessage(Texts.msg(plugin.messages(), "errors.transaction-failed")); return; }

            var removedFromBuyer = ItemUtils.removeSimilar(buyerInv, costT, costAmt);
            if (removedFromBuyer.isEmpty()) {
                ItemUtils.rollbackRemove(contInv, sellT, removedFromCont);
                buyer.sendMessage(Texts.msg(plugin.messages(), "errors.transaction-failed"));
                return;
            }

            
            ItemUtils.addExact(buyerInv, sellT, sellAmt);

            
            vault.deposit(shop.owner(), costT, costAmt);

            
            buyer.sendMessage(Texts.fmt(plugin.messages(), "info.buy-success",
                    "sell_amt", sellAmt, "sell_name", ItemUtils.niceName(sellT),
                    "cost_amt", costAmt, "cost_name", ItemUtils.niceName(costT)));

            
            mgr.requestSignRefresh(shop);
        } finally {
            lock.unlock();
        }
    }
}
