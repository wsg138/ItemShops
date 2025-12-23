package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Pos;
import dev.enthusia.itemshops.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
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

public final class CreateShopMenu implements ShopMenu {
    private static final int SIZE = 54;
    private static final int SELL_SLOT = 20;
    private static final int COST_SLOT = 24;
    private static final int CANCEL_SLOT  = 47;
    private static final int CONFIRM_SLOT = 51;

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player player;
    private final Sign sign;
    private final Block container;

    private final boolean isBedrock;

    private final Inventory inv;

    public CreateShopMenu(ItemShopsPlugin plugin, ShopManager mgr, Player player, Sign sign, Block container) {
        this.plugin = plugin; this.mgr = mgr; this.player = player; this.sign = sign; this.container = container;
        this.isBedrock = Bedrock.isBedrock(player);

        String baseTitle = plugin.getConfig().getString("gui.create-title","Create Item Shop");
        String title = ItemUtils.colored(baseTitle);
        this.inv = Bukkit.createInventory(this, SIZE, title);
        decorate();
    }

    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return player; }
    public void open(){ player.openInventory(inv); }

    
    private ItemStack pane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta();
        gm.setDisplayName(ItemUtils.colored(plugin.getConfig().getString("gui.glass-name"," ")));
        glass.setItemMeta(gm);
        return glass;
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta(); im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im);
        return i;
    }

    private void decorate() {
        
        if (!isBedrock) {
            ItemStack glass = pane();
            for (int i=0;i<SIZE;i++) inv.setItem(i, glass);
        } else {
            
            for (int i=0;i<SIZE;i++) inv.setItem(i, null);
        }

        inv.setItem(SELL_SLOT, null);
        inv.setItem(COST_SLOT, null);

        
        Material sellLabelMat = isBedrock ? Material.LIME_CONCRETE   : Material.LIME_STAINED_GLASS_PANE;
        Material costLabelMat = isBedrock ? Material.YELLOW_CONCRETE : Material.YELLOW_STAINED_GLASS_PANE;

        
        if (SELL_SLOT - 9 >= 0) {
            inv.setItem(SELL_SLOT - 9, named(sellLabelMat, "&aSELL &7(from chest)", List.of("&7Amount buyer receives")));
        }
        if (COST_SLOT - 9 >= 0) {
            inv.setItem(COST_SLOT - 9, named(costLabelMat, "&eRECEIVE &7(buyer pays)", List.of("&7Amount buyer must pay")));
        }

        
        inv.setItem(CONFIRM_SLOT, named(
                Material.LIME_CONCRETE,
                plugin.getConfig().getString("gui.confirm-name","Confirm"),
                List.of("&7Create the shop with these two templates")
        ));
        inv.setItem(CANCEL_SLOT, named(
                Material.RED_CONCRETE,
                plugin.getConfig().getString("gui.cancel-name","Cancel"),
                List.of()
        ));

        
        if (!isBedrock && SIZE >= 54) {
            ItemStack glass = pane();
            for (int i = SIZE - 9; i < SIZE; i++) {
                if (inv.getItem(i) == null) inv.setItem(i, glass);
            }
        }
    }

    private void returnSlot(int slot) {
        ItemStack i = inv.getItem(slot);
        if (i != null && i.getType() != Material.AIR) {
            player.getInventory().addItem(i.clone());
            inv.setItem(slot, null);
        }
    }

    private void confirm() {
        ItemStack sell = inv.getItem(SELL_SLOT);
        ItemStack cost = inv.getItem(COST_SLOT);
        if (sell == null || sell.getType().isAir() || cost == null || cost.getType().isAir()) {
            player.sendMessage(Texts.msg(plugin.messages(), "errors.nothing-to-confirm"));
            return;
        }
        if (container == null || !mgr.isAllowedContainer(container.getType())) {
            player.sendMessage(Texts.msg(plugin.messages(), "errors.no-container"));
            return;
        }

        
        if (!mgr.canCreate(player)) {
            player.sendMessage(Texts.msg(plugin.messages(), "errors.max-shops"));
            return;
        }

        Pos contPos = mgr.unifyContainerPos(container);

        List<Shop> onThis = mgr.shopsOn(contPos);
        int limit = plugin.getConfig().getInt("max-shops-per-container", 2);
        if (!onThis.isEmpty()) {
            boolean otherOwner = onThis.stream().anyMatch(s -> !s.owner().equals(player.getUniqueId()));
            if (plugin.getConfig().getBoolean("one-owner-per-container", true) && otherOwner) {
                player.sendMessage(Texts.msg(plugin.messages(), "errors.container-claimed"));
                return;
            }
        }
        if (onThis.size() >= limit) {
            player.sendMessage(Texts.fmt(plugin.messages(), "errors.container-shop-limit", "limit", limit));
            return;
        }

        
        String violationKey = mgr.canCreateHere(contPos);
        if (violationKey != null) {
            if ("errors.container-shop-limit".equals(violationKey)) {
                player.sendMessage(Texts.fmt(plugin.messages(), violationKey, "limit", limit));
            } else {
                player.sendMessage(Texts.msg(plugin.messages(), violationKey));
            }
            return;
        }

        Pos signPos = Pos.of(sign.getLocation());
        Shop shop = new Shop(signPos, contPos, player.getUniqueId(), sell.clone(), cost.clone());

        
        boolean defSearch = plugin.market().isInMarket(contPos);
        shop.setSearchEnabled(defSearch);

        mgr.put(shop);

        returnSlot(SELL_SLOT);
        returnSlot(COST_SLOT);
        player.closeInventory();
        player.sendMessage(Texts.msg(plugin.messages(), "info.created"));
    }

    @Override
    public boolean onClick(InventoryClickEvent e) {
        if (!e.getView().getTopInventory().equals(inv)) return false;

        int raw = e.getRawSlot();
        boolean top = raw < inv.getSize();

        if (top) {
            if (raw == SELL_SLOT || raw == COST_SLOT) { e.setCancelled(false); return false; }
            e.setCancelled(true);
            if (raw == CONFIRM_SLOT) { confirm(); return true; }
            if (raw == CANCEL_SLOT)  { returnSlot(SELL_SLOT); returnSlot(COST_SLOT); player.closeInventory(); return true; }
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
        for (int s : e.getRawSlots())
            if (s < inv.getSize() && s != SELL_SLOT && s != COST_SLOT) { e.setCancelled(true); return true; }
        return false;
    }

    @Override
    public void onClose(InventoryCloseEvent e) {
        
        returnSlot(SELL_SLOT);
        returnSlot(COST_SLOT);
    }
}
