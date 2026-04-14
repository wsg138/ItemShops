package dev.enthusia.itemshops.manager;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Integration between ItemShops and ARM-Guilds-Bridge for guild shop functionality.
 * Uses cached reflection to avoid classloader issues while maintaining performance.
 * All Method objects are cached at initialization - zero reflection overhead at runtime.
 *
 * Handles:
 * - Checking if a shop is guild-owned
 * - Routing shop income to guild vault instead of player vault
 * - Applying guild permissions to shop access/editing
 */
public class GuildShopIntegration {

    private final ItemShopsPlugin plugin;
    private final Logger logger;

    // Service objects (obtained via reflection at startup)
    private Object armBridge;
    private Object lumaGuilds;
    private Object itemShopGuildService;
    private Object guildService;
    private Object guildVaultService;
    private Object physicalCurrencyService;
    private Object rankService;

    // Cached Method objects (reflection done once at startup, reused at runtime)
    private Method method_isGuildItemShop;
    private Method method_getGuildForItemShop;
    private Method method_getGuild;
    private Method method_depositToVault;
    private Method method_hasPermission;
    private Method method_getCurrencyMaterialName;
    private Method method_getItemValue;

    // Cached classes and enum values
    private Class<?> class_RankPermission;
    private Class<?> class_Guild;
    private Object enum_ACCESS_SHOP_CHESTS;
    private Object enum_EDIT_SHOP_STOCK;
    private Object enum_MODIFY_SHOP_PRICES;

    private boolean enabled = false;

    public GuildShopIntegration(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        initialize();
    }

    /**
     * Initialize integration with ARM-Guilds-Bridge and LumaGuilds.
     * All reflection is done HERE at startup - runtime methods use cached objects.
     */
    private void initialize() {
        try {
            // Check for ARM-Guilds-Bridge
            Plugin armPlugin = Bukkit.getPluginManager().getPlugin("ARM-Guilds-Bridge");
            if (armPlugin == null || !armPlugin.isEnabled()) {
                logger.info("ARM-Guilds-Bridge not found - guild shop integration disabled");
                return;
            }

            armBridge = armPlugin;

            // Check for LumaGuilds
            Plugin lgPlugin = Bukkit.getPluginManager().getPlugin("LumaGuilds");
            if (lgPlugin == null || !lgPlugin.isEnabled()) {
                logger.warning("LumaGuilds not found - guild shop integration disabled");
                return;
            }

            lumaGuilds = lgPlugin;

            // === GET SERVICE OBJECTS (reflection only during init) ===
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

            // === CACHE ALL METHOD OBJECTS (no reflection needed at runtime) ===
            method_isGuildItemShop = itemShopGuildService.getClass().getMethod("isGuildItemShop", Location.class);
            method_getGuildForItemShop = itemShopGuildService.getClass().getMethod("getGuildForItemShop", Location.class);
            method_getGuild = guildService.getClass().getMethod("getGuild", UUID.class);
            method_getCurrencyMaterialName = physicalCurrencyService.getClass().getMethod("getCurrencyMaterialName");
            method_getItemValue = physicalCurrencyService.getClass().getMethod("getItemValue");

            // === CACHE CLASSES AND ENUM VALUES ===
            class_RankPermission = Class.forName("net.lumalyte.lg.domain.entities.RankPermission");
            enum_ACCESS_SHOP_CHESTS = Enum.valueOf((Class<Enum>) class_RankPermission, "ACCESS_SHOP_CHESTS");
            enum_EDIT_SHOP_STOCK = Enum.valueOf((Class<Enum>) class_RankPermission, "EDIT_SHOP_STOCK");
            enum_MODIFY_SHOP_PRICES = Enum.valueOf((Class<Enum>) class_RankPermission, "MODIFY_SHOP_PRICES");

            // Cache hasPermission method (same signature for all permission checks)
            method_hasPermission = rankService.getClass().getMethod("hasPermission",
                UUID.class, UUID.class, class_RankPermission);

            // Get Guild class for depositToVault signature
            class_Guild = Class.forName("net.lumalyte.lg.domain.entities.Guild");
            method_depositToVault = guildVaultService.getClass().getMethod("depositToVault",
                class_Guild, double.class, String.class);

            enabled = true;
            logger.info("Guild shop integration enabled!");
            logger.info("- All reflection cached at startup (zero runtime overhead)");
            logger.info("- Guild shops can be created with /guild setshop");
            logger.info("- Income from guild shops routes to guild vault");
        } catch (Exception e) {
            logger.warning("Failed to initialize guild shop integration: " + e.getMessage());
            e.printStackTrace();
            enabled = false;
        }
    }

    /**
     * Check if guild shop integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if a shop at this location is a guild shop.
     * Uses cached Method - no reflection lookup overhead.
     */
    public boolean isGuildShop(Location shopLocation) {
        if (!enabled) return false;
        try {
            return (Boolean) method_isGuildItemShop.invoke(itemShopGuildService, shopLocation);
        } catch (Exception e) {
            logger.warning("Failed to check if shop is guild shop: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get the guild ID for a shop.
     * Uses cached Method - no reflection lookup overhead.
     */
    public UUID getGuildForShop(Location shopLocation) {
        if (!enabled) return null;
        try {
            return (UUID) method_getGuildForItemShop.invoke(itemShopGuildService, shopLocation);
        } catch (Exception e) {
            logger.warning("Failed to get guild for shop: " + e.getMessage());
            return null;
        }
    }

    /**
     * Route shop income to the appropriate vault (guild or player).
     * Uses cached Method objects - no reflection lookup overhead.
     */
    public boolean routeShopIncome(Location shopLocation, double amount, Player buyer) {
        if (!enabled) {
            // Guild integration disabled - income goes to player normally
            return false;
        }

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) {
            // Not a guild shop - income goes to player normally
            return false;
        }

        // This is a guild shop - route income to guild vault
        try {
            // Fetch guild object (using cached method)
            Object guild = method_getGuild.invoke(guildService, guildId);

            if (guild == null) {
                logger.warning("Guild not found for ID: " + guildId);
                return false;
            }

            // Deposit to guild vault (using cached method)
            method_depositToVault.invoke(guildVaultService, guild, amount, "ItemShop income from " + buyer.getName());

            logger.info("Routed " + amount + " from ItemShop to guild " + guildId + " (buyer: " + buyer.getName() + ")");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to route shop income to guild vault: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a player can access a guild shop (chest opening).
     * Uses cached Method and enum - no reflection lookup overhead.
     */
    public boolean canAccessGuildShop(Player player, Location shopLocation) {
        if (!enabled) return true; // No guild integration, allow normal access

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true; // Not a guild shop, allow normal access

        // Check permission using cached method and enum value
        try {
            return (Boolean) method_hasPermission.invoke(rankService,
                player.getUniqueId(), guildId, enum_ACCESS_SHOP_CHESTS);
        } catch (Exception e) {
            logger.warning("Failed to check guild shop access permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a player can edit a guild shop's stock (inventory editing).
     * Uses cached Method and enum - no reflection lookup overhead.
     */
    public boolean canEditGuildShopStock(Player player, Location shopLocation) {
        if (!enabled) return true; // No guild integration, allow normal editing

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true; // Not a guild shop, allow normal editing

        // Check permission using cached method and enum value
        try {
            return (Boolean) method_hasPermission.invoke(rankService,
                player.getUniqueId(), guildId, enum_EDIT_SHOP_STOCK);
        } catch (Exception e) {
            logger.warning("Failed to check guild shop edit permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a player can modify shop prices (sign editing).
     * Uses cached Method and enum - no reflection lookup overhead.
     */
    public boolean canModifyGuildShopPrices(Player player, Location shopLocation) {
        if (!enabled) return true; // No guild integration, allow normal modification

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true; // Not a guild shop, allow normal modification

        // Check permission using cached method and enum value
        try {
            return (Boolean) method_hasPermission.invoke(rankService,
                player.getUniqueId(), guildId, enum_MODIFY_SHOP_PRICES);
        } catch (Exception e) {
            logger.warning("Failed to check guild shop price modification permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if an ItemStack is the configured physical currency material.
     * Uses cached Method - no reflection lookup overhead.
     */
    public boolean isPhysicalCurrency(ItemStack item) {
        if (!enabled) return false;
        if (item == null) return false;

        try {
            String currencyMaterial = (String) method_getCurrencyMaterialName.invoke(physicalCurrencyService);
            return item.getType().name().equals(currencyMaterial);
        } catch (Exception e) {
            logger.warning("Failed to check if item is physical currency: " + e.getMessage());
            return false;
        }
    }

    /**
     * Calculate the currency value of an ItemStack in currency units.
     * Uses cached Method - no reflection lookup overhead.
     */
    public int calculateCurrencyValue(ItemStack item, int amount) {
        if (!enabled) return 0;
        if (item == null) return 0;

        try {
            if (!isPhysicalCurrency(item)) return 0;

            int itemValue = (Integer) method_getItemValue.invoke(physicalCurrencyService);
            return itemValue * amount;
        } catch (Exception e) {
            logger.warning("Failed to calculate currency value: " + e.getMessage());
            return 0;
        }
    }
}
