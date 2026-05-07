package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.service.ShopTradeService;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Texts;
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
    private static final int SELL_SLOT = 20;
    private static final int COST_SLOT = 24;
    private static final int STATUS_SLOT = 31;
    private static final int CANCEL_SLOT = 4;
    private static final int BUY1_SLOT = 47;
    private static final int CUSTOM_SLOT = 49;
    private static final int MAX_SLOT = 51;

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player buyer;
    private final Shop shop;
    private final boolean isBedrock;
    private final Inventory inv;

    public PurchaseMenu(ItemShopsPlugin plugin, ShopManager mgr, Player buyer, Shop shop) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.buyer = buyer;
        this.shop = shop;
        this.isBedrock = Bedrock.isBedrock(buyer);

        String baseTitle = plugin.getConfig().getString("gui.purchase-title", "Purchase from Shop");
        this.inv = Bukkit.createInventory(this, SIZE, ItemUtils.colored(baseTitle));
        decorate();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @Override
    public HumanEntity viewer() {
        return buyer;
    }

    private ItemStack pane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(ItemUtils.colored(plugin.getConfig().getString("gui.glass-name", " ")));
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack label(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore.stream().map(ItemUtils::colored).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private void decorate() {
        if (!isBedrock) {
            ItemStack glass = pane();
            for (int i = 0; i < SIZE; i++) inv.setItem(i, glass);
        } else {
            for (int i = 0; i < SIZE; i++) inv.setItem(i, null);
        }

        ItemStack sell = shop.sell().clone();
        ItemMeta sellMeta = sell.getItemMeta();
        sellMeta.setDisplayName(ItemUtils.colored("&aYOU RECEIVE"));
        sellMeta.setLore(List.of(ItemUtils.colored("&7Per trade")));
        sell.setItemMeta(sellMeta);

        ItemStack cost = shop.cost().clone();
        ItemMeta costMeta = cost.getItemMeta();
        costMeta.setDisplayName(ItemUtils.colored("&eYOU PAY"));
        costMeta.setLore(List.of(ItemUtils.colored("&7Per trade")));
        cost.setItemMeta(costMeta);

        inv.setItem(SELL_SLOT, sell);
        inv.setItem(COST_SLOT, cost);
        inv.setItem(CANCEL_SLOT, label(Material.BARRIER, plugin.getConfig().getString("gui.cancel-name", "Cancel"), List.of()));
        inv.setItem(BUY1_SLOT, label(Material.LIME_CONCRETE, "&aPurchase 1 Trade", List.of("&7Buy exactly 1 trade")));
        inv.setItem(CUSTOM_SLOT, label(Material.CYAN_CONCRETE, "&bPurchase Custom Amount", List.of("&7Type amount in chat")));
        inv.setItem(MAX_SLOT, label(Material.YELLOW_CONCRETE, "&ePurchase Max", List.of("&7Buy as many as possible")));

        refreshStatus();

        if (!isBedrock && SIZE >= 54) {
            ItemStack glass = pane();
            for (int i = 45; i < 54; i++) {
                if (inv.getItem(i) == null) inv.setItem(i, glass);
            }
        }
    }

    public void open() {
        buyer.openInventory(inv);
    }

    private void refreshStatus() {
        int stockTrades = computeStockTrades();
        int canAffordTrades = computeAffordableTrades();
        ItemStack status = new ItemStack(Material.PAPER);
        ItemMeta meta = status.getItemMeta();
        meta.setDisplayName(ItemUtils.colored("&bStatus"));

        List<String> lore = new ArrayList<>();
        lore.add(ItemUtils.colored("&7Stock trades available: &f" + stockTrades));
        lore.add(ItemUtils.colored("&7You can afford: &f" + canAffordTrades));

        meta.setLore(lore);
        status.setItemMeta(meta);
        inv.setItem(STATUS_SLOT, status);
    }

    private int computeStockTrades() {
        return mgr.cachedTradesAvailable(shop);
    }

    private int computeAffordableTrades() {
        int have = ItemUtils.countSimilar(buyer.getInventory(), shop.cost());
        return have / Math.max(1, shop.cost().getAmount());
    }

    private int computeCapacity(Inventory inventory, ItemStack template) {
        if (template == null || template.getType().isAir()) return 0;
        int max = template.getMaxStackSize();
        int addable = 0;
        for (ItemStack current : inventory.getStorageContents()) {
            if (current == null || current.getType() == Material.AIR) addable += max;
            else if (current.isSimilar(template)) addable += (max - current.getAmount());
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

        int tradesByStock = ItemUtils.countSimilar(contInv, sellT) / Math.max(1, sellT.getAmount());
        int tradesByWallet = ItemUtils.countSimilar(buyerInv, costT) / Math.max(1, costT.getAmount());
        int capacityBuyer = computeCapacity(buyerInv, sellT) / Math.max(1, sellT.getAmount());

        return Math.max(0, Math.min(Math.min(tradesByStock, tradesByWallet), capacityBuyer));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!event.getView().getTopInventory().equals(inv)) return false;

        int raw = event.getRawSlot();
        boolean top = raw < inv.getSize();

        if (top) {
            event.setCancelled(true);
            if (raw == CANCEL_SLOT) {
                buyer.closeInventory();
                return true;
            }
            if (raw == BUY1_SLOT) {
                performPurchase(1);
                buyer.closeInventory();
                return true;
            }
            if (raw == MAX_SLOT) {
                int max = computeMaxTrades();
                if (max <= 0) {
                    buyer.sendMessage(Texts.msg(plugin.messages(), "errors.buymax-none"));
                    return true;
                }
                performPurchase(max);
                buyer.closeInventory();
                return true;
            }
            if (raw == CUSTOM_SLOT) {
                ChatAmountCapture.request(buyer, "Enter number of trades to purchase", amount -> {
                    int max = computeMaxTrades();
                    if (max <= 0) {
                        buyer.sendMessage(Texts.msg(plugin.messages(), "errors.buymax-none"));
                        return;
                    }
                    if (amount > max) amount = max;
                    performPurchase(amount);
                    buyer.closeInventory();
                });
                return true;
            }
            return true;
        }

        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.HOTBAR_SWAP
                || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD
                || event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean onDrag(InventoryDragEvent event) {
        if (!event.getView().getTopInventory().equals(inv)) return false;
        for (int slot : event.getRawSlots()) {
            if (slot < inv.getSize()) {
                event.setCancelled(true);
                return true;
            }
        }
        return true;
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
    }

    private void performPurchase(int trades) {
        ShopTradeService.TradeResult result = plugin.tradeService().executePurchase(buyer, shop, trades);
        if (!result.success()) {
            if (result.errorMessageKey() != null) {
                buyer.sendMessage(Texts.msg(plugin.messages(), result.errorMessageKey()));
            }
            return;
        }
        buyer.sendMessage(Texts.fmt(
                plugin.messages(),
                "info.buy-success",
                "sell_amt", result.sellAmount(),
                "sell_name", ItemUtils.niceName(result.sellItem()),
                "cost_amt", result.costAmount(),
                "cost_name", ItemUtils.niceName(result.costItem())
        ));
    }
}
