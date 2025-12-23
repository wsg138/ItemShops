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

import java.util.List;

public final class ShopEditMenu implements ShopMenu {
    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player owner;
    private final Shop shop;
    private final Inventory inv;
    private final boolean allowTemplateEdit;
    private final boolean allowDelete;

    private static final int SIZE = 54;
    private static final int SELL_SLOT = 20; 
    private static final int COST_SLOT = 24; 
    private static final int SELL_LABEL = SELL_SLOT - 9; 
    private static final int COST_LABEL = COST_SLOT - 9; 
    private static final int HOP_IN_SLOT  = 21;
    private static final int SEARCH_SLOT  = 22; 
    private static final int HOP_OUT_SLOT = 23;
    private static final int SAVE_SLOT   = 47;
    private static final int CLOSE_SLOT  = 49;
    private static final int DELETE_SLOT = 51;

    private final boolean bedrockViewer;

    public ShopEditMenu(ItemShopsPlugin plugin, ShopManager mgr, Player owner, Shop shop, boolean allowTemplateEdit, boolean allowDelete) {
        this.plugin = plugin; this.mgr = mgr; this.owner = owner; this.shop = shop;
        this.allowTemplateEdit = allowTemplateEdit;
        this.allowDelete = allowDelete;

        this.bedrockViewer = Bedrock.isBedrock(owner);

        String title = ItemUtils.colored(plugin.getConfig().getString("gui.edit-title","Edit Shop"));
        this.inv = Bukkit.createInventory(this, SIZE, title);
        render();
    }

    @Override public Inventory getInventory(){ return inv; }
    @Override public HumanEntity viewer(){ return owner; }
    public void open(){ owner.openInventory(inv); }

    

    private ItemStack pane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gm = glass.getItemMeta(); gm.setDisplayName(" "); glass.setItemMeta(gm);
        return glass;
    }

    private ItemStack named(Material m, String name, List<String> lore) {
        ItemStack i = new ItemStack(m);
        ItemMeta im = i.getItemMeta(); im.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) im.setLore(lore.stream().map(ItemUtils::colored).toList());
        i.setItemMeta(im);
        return i;
    }

    private ItemStack hopperToggle(boolean enabled, boolean isIn) {
        Material mat = enabled ? Material.LIME_DYE : Material.RED_DYE;
        String title = (isIn ? "&bHoppers INTO chest: " : "&bHoppers OUT of chest: ") + (enabled ? "&aENABLED" : "&cDISABLED");
        List<String> lore = List.of(
                "&7Click to " + (enabled ? "disable" : "enable"),
                isIn ? "&8Allow hoppers to feed stock into this chest."
                        : "&8Allow hoppers to extract items from this chest."
        );
        return named(mat, title, lore);
    }

    private ItemStack searchToggle(boolean enabled) {
        Material mat = enabled ? Material.GLOWSTONE_DUST : Material.GUNPOWDER;
        String title = enabled ? "&aInclude in Search" : "&cHidden from Search";
        List<String> lore = List.of("&7Toggle whether this shop appears in /shops search");
        return named(mat, title, lore);
    }

    private void render() {
        
        if (!bedrockViewer) {
            ItemStack glass = pane();
            for (int i=0;i<SIZE;i++) inv.setItem(i, glass);
        } else {
            for (int i=0;i<SIZE;i++) inv.setItem(i, null);
        }

        
        inv.setItem(SELL_SLOT, shop.sell().clone());
        inv.setItem(COST_SLOT, shop.cost().clone());

        
        Material sellLabelMat = bedrockViewer ? Material.LIME_CONCRETE   : Material.LIME_STAINED_GLASS_PANE;
        Material costLabelMat = bedrockViewer ? Material.YELLOW_CONCRETE : Material.YELLOW_STAINED_GLASS_PANE;

        if (SELL_LABEL >= 0) inv.setItem(SELL_LABEL, named(sellLabelMat, "&aGIVE (from chest)", List.of()));
        if (COST_LABEL >= 0) inv.setItem(COST_LABEL, named(costLabelMat, "&eGET (buyer pays)", List.of()));

        
        inv.setItem(HOP_IN_SLOT,  hopperToggle(shop.isHopperAllowIn(), true));
        inv.setItem(SEARCH_SLOT,  searchToggle(shop.isSearchEnabled()));
        inv.setItem(HOP_OUT_SLOT, hopperToggle(shop.isHopperAllowOut(), false));

        
        inv.setItem(SAVE_SLOT,   named(Material.LIME_CONCRETE, plugin.getConfig().getString("gui.save-name","Save Changes"), List.of("&7Apply changes")));
        inv.setItem(CLOSE_SLOT,  named(Material.BARRIER, "&cClose", List.of("&7Exit without changing sign")));
        inv.setItem(DELETE_SLOT, named(Material.RED_CONCRETE,  plugin.getConfig().getString("gui.delete-name","Delete Shop"), List.of("&7Delete this shop")));

        
        if (!bedrockViewer) {
            ItemStack pane2 = pane();
            for (int i=SIZE-9;i<SIZE;i++) if (inv.getItem(i)==null) inv.setItem(i, pane2);
        }
    }

    

    








    private void handleTemplateClick(InventoryClickEvent e, int slot) {
        e.setCancelled(true); 

        ItemStack cursor = e.getCursor();
        ItemStack current = inv.getItem(slot);
        InventoryAction action = e.getAction();
        ClickType click = e.getClick();

        
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.COLLECT_TO_CURSOR
                || click == ClickType.NUMBER_KEY) {
            return; 
        }

        
        if (cursor == null || cursor.getType().isAir()) {
            if (current != null && !current.getType().isAir()) {
                inv.setItem(slot, null); 
            }
            
            return;
        }

        
        ItemStack clone = cursor.clone();
        inv.setItem(slot, clone);
        
    }

    

    @Override
    public boolean onClick(InventoryClickEvent e) {
        int raw = e.getRawSlot();
        boolean top = raw < inv.getSize();

        if (!top) {
            
            InventoryAction action = e.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.HOTBAR_SWAP
                    || action == InventoryAction.HOTBAR_MOVE_AND_READD
                    || action == InventoryAction.COLLECT_TO_CURSOR
                    || e.getClick()  == ClickType.NUMBER_KEY) {
                e.setCancelled(true);
                return true;
            }
            return false;
        }

        
        if (raw == SELL_SLOT || raw == COST_SLOT) {
            if (!allowTemplateEdit) {
                e.setCancelled(true);
                owner.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
                return true;
            }
            handleTemplateClick(e, raw);
            return true;
        }

        
        e.setCancelled(true);

        if (raw == HOP_IN_SLOT)  { shop.setHopperAllowIn(!shop.isHopperAllowIn());  mgr.requestSave(); render(); return true; }
        if (raw == HOP_OUT_SLOT) { shop.setHopperAllowOut(!shop.isHopperAllowOut()); mgr.requestSave(); render(); return true; }
        if (raw == SEARCH_SLOT)  { shop.setSearchEnabled(!shop.isSearchEnabled());   mgr.requestSave(); render(); return true; }

        if (raw == SAVE_SLOT) {
            if (!allowTemplateEdit) {
                owner.sendMessage(ItemUtils.colored("&aSettings updated."));
                owner.closeInventory();
                return true;
            }
            ItemStack sell = inv.getItem(SELL_SLOT);
            ItemStack cost = inv.getItem(COST_SLOT);
            if (sell == null || sell.getType().isAir() || cost == null || cost.getType().isAir()) {
                owner.sendMessage(Texts.msg(plugin.messages(), "errors.nothing-to-confirm"));
                return true;
            }
            shop.setSell(sell.clone());
            shop.setCost(cost.clone());
            plugin.shops().updateSign(shop);
            mgr.requestSave();
            owner.sendMessage(ItemUtils.colored("&aShop updated."));
            owner.closeInventory();
            return true;
        }

        if (raw == CLOSE_SLOT) { owner.closeInventory(); return true; }

        if (raw == DELETE_SLOT) {
            if (!allowDelete) {
                owner.sendMessage(Texts.msg(plugin.messages(), "errors.not-owner"));
                return true;
            }
            mgr.deleteShop(shop);
            owner.sendMessage(ItemUtils.colored("&eShop deleted."));
            owner.closeInventory();
            return true;
        }
        return true;
    }

    @Override
    public boolean onDrag(InventoryDragEvent e) {
        if (!allowTemplateEdit) {
            e.setCancelled(true);
            return true;
        }
        for (int s : e.getRawSlots()) {
            if (s < inv.getSize() && s != SELL_SLOT && s != COST_SLOT) {
                e.setCancelled(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose(InventoryCloseEvent e) { }
}
