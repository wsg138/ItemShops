package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.util.Bedrock;
import dev.enthusia.itemshops.util.ItemUtils;
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
    private static final int CANCEL_SLOT = 47;
    private static final int CONFIRM_SLOT = 51;

    private final ItemShopsPlugin plugin;
    private final ShopManager mgr;
    private final Player player;
    private final Sign sign;
    private final Block container;
    private final boolean isBedrock;
    private final Inventory inv;

    public CreateShopMenu(ItemShopsPlugin plugin, ShopManager mgr, Player player, Sign sign, Block container) {
        this.plugin = plugin;
        this.mgr = mgr;
        this.player = player;
        this.sign = sign;
        this.container = container;
        this.isBedrock = Bedrock.isBedrock(player);

        String title = ItemUtils.colored(plugin.getConfig().getString("gui.create-title", "Create Item Shop"));
        this.inv = Bukkit.createInventory(this, SIZE, title);
        decorate();
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    @Override
    public HumanEntity viewer() {
        return player;
    }

    public void open() {
        player.openInventory(inv);
    }

    private ItemStack pane() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(ItemUtils.colored(plugin.getConfig().getString("gui.glass-name", " ")));
        glass.setItemMeta(meta);
        return glass;
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ItemUtils.colored(name));
        if (lore != null && !lore.isEmpty()) meta.setLore(lore.stream().map(ItemUtils::colored).toList());
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

        inv.setItem(SELL_SLOT, null);
        inv.setItem(COST_SLOT, null);

        Material sellLabelMat = isBedrock ? Material.LIME_CONCRETE : Material.LIME_STAINED_GLASS_PANE;
        Material costLabelMat = isBedrock ? Material.YELLOW_CONCRETE : Material.YELLOW_STAINED_GLASS_PANE;

        if (SELL_SLOT - 9 >= 0) {
            inv.setItem(SELL_SLOT - 9, named(sellLabelMat, "&aSELL &7(from chest)", List.of("&7Amount buyer receives")));
        }
        if (COST_SLOT - 9 >= 0) {
            inv.setItem(COST_SLOT - 9, named(costLabelMat, "&eRECEIVE &7(buyer pays)", List.of("&7Amount buyer must pay")));
        }

        inv.setItem(CONFIRM_SLOT, named(
                Material.LIME_CONCRETE,
                plugin.getConfig().getString("gui.confirm-name", "Confirm"),
                List.of("&7Create the shop with these two templates")
        ));
        inv.setItem(CANCEL_SLOT, named(
                Material.RED_CONCRETE,
                plugin.getConfig().getString("gui.cancel-name", "Cancel"),
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
        ItemStack item = inv.getItem(slot);
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item.clone());
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
            player.sendMessage(Texts.fmt(plugin.messages(), "errors.max-shops", "max", plugin.pluginConfig().maxShopsPerPlayer()));
            return;
        }

        var contPos = mgr.unifyContainerPos(container);
        int limit = plugin.pluginConfig().maxShopsPerContainer();
        String violationKey = mgr.validatePlacement(player, contPos);
        if (violationKey != null) {
            if ("errors.container-shop-limit".equals(violationKey)) {
                player.sendMessage(Texts.fmt(plugin.messages(), violationKey, "limit", limit));
            } else {
                player.sendMessage(Texts.msg(plugin.messages(), violationKey));
            }
            return;
        }

        mgr.createShop(sign, container, player, sell, cost);
        returnSlot(SELL_SLOT);
        returnSlot(COST_SLOT);
        player.closeInventory();
        player.sendMessage(Texts.msg(plugin.messages(), "info.created"));
    }

    @Override
    public boolean onClick(InventoryClickEvent event) {
        if (!event.getView().getTopInventory().equals(inv)) return false;

        int raw = event.getRawSlot();
        boolean top = raw < inv.getSize();

        if (top) {
            if (raw == SELL_SLOT || raw == COST_SLOT) {
                event.setCancelled(false);
                return false;
            }
            event.setCancelled(true);
            if (raw == CONFIRM_SLOT) {
                confirm();
                return true;
            }
            if (raw == CANCEL_SLOT) {
                returnSlot(SELL_SLOT);
                returnSlot(COST_SLOT);
                player.closeInventory();
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
            if (slot < inv.getSize() && slot != SELL_SLOT && slot != COST_SLOT) {
                event.setCancelled(true);
                return true;
            }
        }
        return false;
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        returnSlot(SELL_SLOT);
        returnSlot(COST_SLOT);
    }
}
