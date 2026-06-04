package dev.enthusia.itemshops.manager;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GuildShopIntegration {

    private final ItemShopsPlugin plugin;
    private final Logger logger;

    private Object armBridge;
    private Object lumaGuilds;
    private Object itemShopGuildService;
    private Object guildService;
    private Object guildVaultService;
    private Object physicalCurrencyService;
    private Object rankService;

    private Method methodIsGuildItemShop;
    private Method methodGetGuildForItemShop;
    private Method methodGetGuild;
    private Method methodDepositToVault;
    private Method methodHasPermission;
    private Method methodGetCurrencyMaterialName;
    private Method methodGetItemValue;

    private Object enumAccessShopChests;
    private Object enumEditShopStock;
    private Object enumModifyShopPrices;

    private boolean enabled;

    public GuildShopIntegration(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        initialize();
    }

    private void initialize() {
        try {
            Plugin armPlugin = Bukkit.getPluginManager().getPlugin("ARM-Guilds-Bridge");
            if (armPlugin == null || !armPlugin.isEnabled()) {
                logger.info("ARM-Guilds-Bridge not found - guild shop integration disabled");
                return;
            }
            armBridge = armPlugin;

            Plugin lgPlugin = Bukkit.getPluginManager().getPlugin("LumaGuilds");
            if (lgPlugin == null || !lgPlugin.isEnabled()) {
                logger.warning("LumaGuilds not found - guild shop integration disabled");
                return;
            }
            lumaGuilds = lgPlugin;

            Method getItemShopGuildService = armBridge.getClass().getMethod("getItemShopGuildService");
            itemShopGuildService = getItemShopGuildService.invoke(armBridge);

            Method getGuildService = lumaGuilds.getClass().getMethod("getGuildService");
            guildService = getGuildService.invoke(lumaGuilds);

            Method getGuildVaultService = lumaGuilds.getClass().getMethod("getGuildVaultService");
            guildVaultService = getGuildVaultService.invoke(lumaGuilds);

            Method getPhysicalCurrencyService = lumaGuilds.getClass().getMethod("getPhysicalCurrencyService");
            physicalCurrencyService = getPhysicalCurrencyService.invoke(lumaGuilds);

            Method getRankService = armBridge.getClass().getMethod("getRankService");
            rankService = getRankService.invoke(armBridge);

            methodIsGuildItemShop = itemShopGuildService.getClass().getMethod("isGuildItemShop", Location.class);
            methodGetGuildForItemShop = itemShopGuildService.getClass().getMethod("getGuildForItemShop", Location.class);
            methodGetGuild = guildService.getClass().getMethod("getGuild", UUID.class);
            methodGetCurrencyMaterialName = physicalCurrencyService.getClass().getMethod("getCurrencyMaterialName");
            methodGetItemValue = physicalCurrencyService.getClass().getMethod("getItemValue");

            Class<?> rankPermissionClass = Class.forName("net.lumalyte.lg.domain.entities.RankPermission");
            enumAccessShopChests = Enum.valueOf((Class<Enum>) rankPermissionClass, "ACCESS_SHOP_CHESTS");
            enumEditShopStock = Enum.valueOf((Class<Enum>) rankPermissionClass, "EDIT_SHOP_STOCK");
            enumModifyShopPrices = Enum.valueOf((Class<Enum>) rankPermissionClass, "MODIFY_SHOP_PRICES");

            methodHasPermission = rankService.getClass().getMethod(
                    "hasPermission",
                    UUID.class,
                    UUID.class,
                    rankPermissionClass
            );

            Class<?> guildClass = Class.forName("net.lumalyte.lg.domain.entities.Guild");
            methodDepositToVault = guildVaultService.getClass().getMethod(
                    "depositToVault",
                    guildClass,
                    double.class,
                    String.class
            );

            enabled = true;
            logger.info("Guild shop integration enabled!");
            logger.info("- All reflection cached at startup (zero runtime overhead)");
            logger.info("- Guild shops can be created with /guild setshop");
            logger.info("- Income from guild shops routes to guild vault");
        } catch (Exception e) {
            enabled = false;
            logger.log(Level.WARNING, "Failed to initialize guild shop integration.", e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isGuildShop(Location shopLocation) {
        if (!enabled || shopLocation == null) return false;
        try {
            return (Boolean) methodIsGuildItemShop.invoke(itemShopGuildService, shopLocation);
        } catch (Exception e) {
            logger.warning("Failed to check if shop is guild shop: " + e.getMessage());
            return false;
        }
    }

    public UUID getGuildForShop(Location shopLocation) {
        if (!enabled || shopLocation == null) return null;
        try {
            return (UUID) methodGetGuildForItemShop.invoke(itemShopGuildService, shopLocation);
        } catch (Exception e) {
            logger.warning("Failed to get guild for shop: " + e.getMessage());
            return null;
        }
    }

    public boolean routeShopIncome(Location shopLocation, int amount, Player buyer) {
        return routeShopIncome(shopLocation, (double) amount, buyer);
    }

    public boolean routeShopIncome(Location shopLocation, double amount, Player buyer) {
        if (!enabled || shopLocation == null || buyer == null || amount <= 0D) {
            return false;
        }

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) {
            return false;
        }

        try {
            Object guild = methodGetGuild.invoke(guildService, guildId);
            if (guild == null) {
                logger.warning("Guild not found for ID: " + guildId);
                return false;
            }

            methodDepositToVault.invoke(guildVaultService, guild, amount, "ItemShop income from " + buyer.getName());
            logger.info("Routed " + amount + " from ItemShop to guild " + guildId + " (buyer: " + buyer.getName() + ")");
            return true;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to route shop income to guild vault.", e);
            return false;
        }
    }

    public boolean canAccessGuildShop(Player player, Location shopLocation) {
        return hasGuildPermission(player, shopLocation, enumAccessShopChests);
    }

    public boolean canEditGuildShopStock(Player player, Location shopLocation) {
        return hasGuildPermission(player, shopLocation, enumEditShopStock);
    }

    public boolean canModifyGuildShopPrices(Player player, Location shopLocation) {
        return hasGuildPermission(player, shopLocation, enumModifyShopPrices);
    }

    private boolean hasGuildPermission(Player player, Location shopLocation, Object permission) {
        if (!enabled || player == null || shopLocation == null) return true;

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true;

        try {
            return (Boolean) methodHasPermission.invoke(rankService, player.getUniqueId(), guildId, permission);
        } catch (Exception e) {
            logger.warning("Failed to check guild permission: " + e.getMessage());
            return false;
        }
    }

    public boolean isPhysicalCurrency(ItemStack item) {
        if (!enabled || item == null) return false;
        try {
            String currencyMaterial = (String) methodGetCurrencyMaterialName.invoke(physicalCurrencyService);
            return item.getType().name().equals(currencyMaterial);
        } catch (Exception e) {
            logger.warning("Failed to check if item is physical currency: " + e.getMessage());
            return false;
        }
    }

    public int calculateCurrencyValue(ItemStack item, int amount) {
        if (!enabled || item == null || amount <= 0) return 0;
        try {
            if (!isPhysicalCurrency(item)) return 0;
            int itemValue = (Integer) methodGetItemValue.invoke(physicalCurrencyService);
            return itemValue * amount;
        } catch (Exception e) {
            logger.warning("Failed to calculate currency value: " + e.getMessage());
            return 0;
        }
    }
}
