package dev.enthusia.itemshops.manager;

import dev.enthusia.itemshops.ItemShopsPlugin;
import net.lumalyte.armbridge.ARMGuildsBridge;
import net.lumalyte.armbridge.services.ItemShopGuildService;
import net.lumalyte.lg.LumaGuilds;
import net.lumalyte.lg.application.services.GuildService;
import net.lumalyte.lg.application.services.GuildVaultService;
import net.lumalyte.lg.application.services.PhysicalCurrencyService;
import net.lumalyte.lg.domain.entities.Guild;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Integration between ItemShops and ARM-Guilds-Bridge for guild shop functionality.
 *
 * Handles:
 * - Checking if a shop is guild-owned
 * - Routing shop income to guild vault instead of player vault
 * - Applying guild permissions to shop access/editing
 */
public class GuildShopIntegration {

    private final ItemShopsPlugin plugin;
    private final Logger logger;

    private ARMGuildsBridge armBridge;
    private LumaGuilds lumaGuilds;
    private ItemShopGuildService itemShopGuildService;
    private GuildService guildService;
    private GuildVaultService guildVaultService;
    private PhysicalCurrencyService physicalCurrencyService;

    private boolean enabled = false;

    public GuildShopIntegration(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        initialize();
    }

    /**
     * Initialize integration with ARM-Guilds-Bridge and LumaGuilds
     */
    private void initialize() {
        // Check for ARM-Guilds-Bridge
        Plugin armPlugin = Bukkit.getPluginManager().getPlugin("ARM-Guilds-Bridge");
        if (armPlugin == null || !armPlugin.isEnabled()) {
            logger.warning("ARM-Guilds-Bridge not found - guild shop integration disabled");
            logger.warning("Guild shops will not be available");
            return;
        }

        if (!(armPlugin instanceof ARMGuildsBridge)) {
            logger.warning("ARM-Guilds-Bridge wrong type - guild shop integration disabled");
            return;
        }

        armBridge = (ARMGuildsBridge) armPlugin;

        // Check for LumaGuilds
        Plugin lgPlugin = Bukkit.getPluginManager().getPlugin("LumaGuilds");
        if (lgPlugin == null || !lgPlugin.isEnabled()) {
            logger.warning("LumaGuilds not found - guild shop integration disabled");
            return;
        }

        if (!(lgPlugin instanceof LumaGuilds)) {
            logger.warning("LumaGuilds wrong type - guild shop integration disabled");
            return;
        }

        lumaGuilds = (LumaGuilds) lgPlugin;

        // Get services
        itemShopGuildService = armBridge.getItemShopGuildService();
        guildService = lumaGuilds.getGuildService();
        guildVaultService = lumaGuilds.getGuildVaultService();
        physicalCurrencyService = lumaGuilds.getPhysicalCurrencyService();

        enabled = true;
        logger.info("Guild shop integration enabled!");
        logger.info("- Guild shops can be created with /guild setshop");
        logger.info("- Income from guild shops routes to guild vault");
    }

    /**
     * Check if guild shop integration is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Check if a shop at this location is a guild shop
     *
     * @param shopLocation Location of the shop chest
     * @return true if this is a guild shop
     */
    public boolean isGuildShop(Location shopLocation) {
        if (!enabled) return false;
        return itemShopGuildService.isGuildItemShop(shopLocation);
    }

    /**
     * Get the guild ID for a shop
     *
     * @param shopLocation Location of the shop chest
     * @return Guild UUID or null if not a guild shop
     */
    public UUID getGuildForShop(Location shopLocation) {
        if (!enabled) return null;
        return itemShopGuildService.getGuildForItemShop(shopLocation);
    }

    /**
     * Route shop income to the appropriate vault (guild or player)
     *
     * @param shopLocation Location of the shop chest
     * @param amount Amount of income
     * @param buyer Player who made the purchase (for logging)
     * @return true if successfully routed
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
            // Fetch guild object
            Guild guild = guildService.getGuild(guildId);
            if (guild == null) {
                logger.warning("Guild not found for ID: " + guildId);
                return false;
            }

            // Deposit to guild vault
            guildVaultService.depositToVault(guild, amount, "ItemShop income from " + buyer.getName());

            logger.info("Routed " + amount + " from ItemShop to guild " + guildId + " (buyer: " + buyer.getName() + ")");
            return true;
        } catch (Exception e) {
            logger.severe("Failed to route shop income to guild vault: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if a player can access a guild shop (chest opening)
     *
     * @param player Player attempting access
     * @param shopLocation Shop location
     * @return true if player has permission
     */
    public boolean canAccessGuildShop(Player player, Location shopLocation) {
        if (!enabled) return true; // No guild integration, allow normal access

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true; // Not a guild shop, allow normal access

        // Check if player has ACCESS_SHOP_CHESTS permission
        try {
            return armBridge.getRankService().hasPermission(
                player.getUniqueId(),
                guildId,
                net.lumalyte.lg.domain.entities.RankPermission.ACCESS_SHOP_CHESTS
            );
        } catch (Exception e) {
            logger.warning("Failed to check guild shop access permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a player can edit a guild shop's stock (inventory editing)
     *
     * @param player Player attempting to edit
     * @param shopLocation Shop location
     * @return true if player has permission
     */
    public boolean canEditGuildShopStock(Player player, Location shopLocation) {
        if (!enabled) return true; // No guild integration, allow normal editing

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true; // Not a guild shop, allow normal editing

        // Check if player has EDIT_SHOP_STOCK permission
        try {
            return armBridge.getRankService().hasPermission(
                player.getUniqueId(),
                guildId,
                net.lumalyte.lg.domain.entities.RankPermission.EDIT_SHOP_STOCK
            );
        } catch (Exception e) {
            logger.warning("Failed to check guild shop edit permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a player can modify shop prices (sign editing)
     *
     * @param player Player attempting to modify
     * @param shopLocation Shop location
     * @return true if player has permission
     */
    public boolean canModifyGuildShopPrices(Player player, Location shopLocation) {
        if (!enabled) return true; // No guild integration, allow normal modification

        UUID guildId = getGuildForShop(shopLocation);
        if (guildId == null) return true; // Not a guild shop, allow normal modification

        // Check if player has MODIFY_SHOP_PRICES permission
        try {
            return armBridge.getRankService().hasPermission(
                player.getUniqueId(),
                guildId,
                net.lumalyte.lg.domain.entities.RankPermission.MODIFY_SHOP_PRICES
            );
        } catch (Exception e) {
            logger.warning("Failed to check guild shop price modification permission: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if an ItemStack is the configured physical currency material
     *
     * @param item ItemStack to check
     * @return true if item matches the physical currency material
     */
    public boolean isPhysicalCurrency(org.bukkit.inventory.ItemStack item) {
        if (!enabled) return false;
        if (item == null) return false;

        try {
            String currencyMaterial = physicalCurrencyService.getCurrencyMaterialName();
            return item.getType().name().equals(currencyMaterial);
        } catch (Exception e) {
            logger.warning("Failed to check if item is physical currency: " + e.getMessage());
            return false;
        }
    }

    /**
     * Calculate the currency value of an ItemStack in currency units
     *
     * @param item ItemStack to calculate
     * @param amount Number of items
     * @return Total currency value
     */
    public int calculateCurrencyValue(org.bukkit.inventory.ItemStack item, int amount) {
        if (!enabled) return 0;
        if (item == null) return 0;

        try {
            if (!isPhysicalCurrency(item)) return 0;
            return physicalCurrencyService.getItemValue() * amount;
        } catch (Exception e) {
            logger.warning("Failed to calculate currency value: " + e.getMessage());
            return 0;
        }
    }
}
