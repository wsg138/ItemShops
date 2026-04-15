package dev.enthusia.itemshops;

import dev.enthusia.itemshops.command.ItemShopsCommand;
import dev.enthusia.itemshops.command.ShopHelpCommand;
import dev.enthusia.itemshops.config.ItemShopsConfig;
import dev.enthusia.itemshops.data.ShopStorage;
import dev.enthusia.itemshops.gui.ChatAmountCapture;
import dev.enthusia.itemshops.listener.BlockProtectionListener;
import dev.enthusia.itemshops.listener.ContainerAccessListener;
import dev.enthusia.itemshops.listener.ContainerStockListener;
import dev.enthusia.itemshops.listener.ExplodeCleanupListener;
import dev.enthusia.itemshops.listener.HopperControlListener;
import dev.enthusia.itemshops.listener.MenuListener;
import dev.enthusia.itemshops.listener.RecoveryListener;
import dev.enthusia.itemshops.listener.SignShopListener;
import dev.enthusia.itemshops.manager.GuildShopIntegration;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.service.ShopLocator;
import dev.enthusia.itemshops.service.ShopPlacementPolicy;
import dev.enthusia.itemshops.service.ShopRegistry;
import dev.enthusia.itemshops.service.ShopSignService;
import dev.enthusia.itemshops.service.ShopTradeService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemShopsPlugin extends JavaPlugin {

    private static ItemShopsPlugin instance;

    private ShopManager shopManager;
    private ShopStorage storage;
    private ItemShopsConfig pluginConfig;
    private dev.enthusia.itemshops.vault.VaultManager vaultManager;
    private dev.enthusia.itemshops.region.MarketRegionManager marketManager;
    private GuildShopIntegration guildShopIntegration;
    private ShopTradeService shopTradeService;

    private final Map<UUID, Long> breakDeleteExpiry = new ConcurrentHashMap<>();
    private final Set<UUID> adminViewEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> adminInfoEnabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> breakOthersEnabled = ConcurrentHashMap.newKeySet();

    public static ItemShopsPlugin get() {
        return instance;
    }

    public dev.enthusia.itemshops.vault.VaultManager vault() {
        return vaultManager;
    }

    public dev.enthusia.itemshops.region.MarketRegionManager market() {
        return marketManager;
    }

    public GuildShopIntegration guildShops() {
        return guildShopIntegration;
    }

    public ShopTradeService tradeService() {
        return shopTradeService;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        ensureConfigDefaults();
        saveResource("messages.yml", false);
        YamlLoader.mergeDefaults(this, "messages.yml");

        pluginConfig = new ItemShopsConfig(this);
        marketManager = new dev.enthusia.itemshops.region.MarketRegionManager(this);
        storage = new ShopStorage(this, pluginConfig, marketManager);
        ShopRegistry registry = new ShopRegistry();
        ShopLocator locator = new ShopLocator(pluginConfig);
        ShopPlacementPolicy placementPolicy = new ShopPlacementPolicy(pluginConfig, marketManager, registry);
        ShopSignService signService = new ShopSignService(this);
        shopManager = new ShopManager(
                this,
                storage,
                pluginConfig,
                registry,
                locator,
                placementPolicy,
                signService
        );
        shopManager.startSignUpdateScheduler();

        getServer().getPluginManager().registerEvents(new SignShopListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ContainerStockListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new ContainerAccessListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new ExplodeCleanupListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new HopperControlListener(shopManager), this);

        vaultManager = new dev.enthusia.itemshops.vault.VaultManager(this);
        guildShopIntegration = new GuildShopIntegration(this);
        shopTradeService = new ShopTradeService(this, shopManager);

        getServer().getPluginManager().registerEvents(new RecoveryListener(this, shopManager, storage), this);

        ChatAmountCapture.register(this);

        ItemShopsCommand mainCmd = new ItemShopsCommand(shopManager);
        if (getCommand("itemshops") != null) {
            getCommand("itemshops").setExecutor(mainCmd);
            getCommand("itemshops").setTabCompleter(mainCmd);
        }

        ShopHelpCommand helpCmd = new ShopHelpCommand(this);
        if (getCommand("shophelp") != null) {
            getCommand("shophelp").setExecutor(helpCmd);
            getCommand("shophelp").setTabCompleter(helpCmd);
        }

        var storeCmd = new dev.enthusia.itemshops.command.StoreCommand();
        if (getCommand("store") != null) {
            getCommand("store").setExecutor(storeCmd);
        }

        var vaultCmd = new dev.enthusia.itemshops.command.VaultCommand(this);
        if (getCommand("shopvault") != null) {
            getCommand("shopvault").setExecutor(vaultCmd);
            getCommand("shopvault").setTabCompleter(vaultCmd);
        }

        var marketCmd = new dev.enthusia.itemshops.command.MarketCommand(this);
        if (getCommand("shopmarket") != null) {
            getCommand("shopmarket").setExecutor(marketCmd);
            getCommand("shopmarket").setTabCompleter(marketCmd);
        }

        shopManager.loadFromStorage();
        int pruned = shopManager.pruneInvalidShops();
        getLogger().info("ItemShops enabled. Loaded " + shopManager.size() + " shops (pruned " + pruned + ").");
    }

    @Override
    public void onDisable() {
        try {
            if (shopManager != null) {
                shopManager.shutdown();
            }
        } catch (Exception e) {
            getLogger().severe("Failed to save shops: " + e.getMessage());
            e.printStackTrace();
        }
        if (vaultManager != null) {
            vaultManager.saveNow();
        }
    }

    public FileConfiguration messages() {
        return YamlLoader.get(this, "messages.yml");
    }

    public ShopManager shops() {
        return shopManager;
    }

    public ShopStorage storage() {
        return storage;
    }

    public ItemShopsConfig pluginConfig() {
        return pluginConfig;
    }

    private boolean ensureConfigDefaults() {
        boolean changed = YamlLoader.mergeDefaults(this, "config.yml");
        if (changed) {
            getLogger().info("config.yml was missing entries; added defaults from the jar.");
        }
        return changed;
    }

    public void reloadAll() {
        ensureConfigDefaults();
        reloadConfig();
        if (pluginConfig != null) {
            pluginConfig.reload();
        }
        YamlLoader.mergeDefaults(this, "messages.yml");
        YamlLoader.reload(this, "messages.yml");
        storage.reloadFile();
        if (marketManager != null) {
            marketManager.reload();
        }

        shopManager.saveNow();
        shopManager.loadFromStorage();
        shopManager.startSignUpdateScheduler();

        int pruned = shopManager.pruneInvalidShops();
        getLogger().info("Reload complete. Shops: " + shopManager.size() + " (pruned " + pruned + ").");
    }

    public void enableBreakDelete(UUID playerId, long durationMs) {
        if (playerId == null) {
            return;
        }
        breakDeleteExpiry.put(playerId, System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    public void disableBreakDelete(UUID playerId) {
        if (playerId == null) {
            return;
        }
        breakDeleteExpiry.remove(playerId);
    }

    public boolean isBreakDeleteActive(UUID playerId) {
        if (playerId == null) {
            return false;
        }
        Long exp = breakDeleteExpiry.get(playerId);
        if (exp == null) {
            return false;
        }
        if (exp < System.currentTimeMillis()) {
            breakDeleteExpiry.remove(playerId);
            return false;
        }
        return true;
    }

    public long breakDeleteRemainingMs(UUID playerId) {
        if (playerId == null) {
            return 0L;
        }
        Long exp = breakDeleteExpiry.get(playerId);
        if (exp == null) {
            return 0L;
        }
        long left = exp - System.currentTimeMillis();
        if (left <= 0) {
            breakDeleteExpiry.remove(playerId);
            return 0L;
        }
        return left;
    }

    public boolean isAdminViewActive(UUID playerId) {
        return playerId != null && adminViewEnabled.contains(playerId);
    }

    public void setAdminView(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled) {
            adminViewEnabled.add(playerId);
        } else {
            adminViewEnabled.remove(playerId);
        }
    }

    public boolean isAdminInfoActive(UUID playerId) {
        return playerId != null && adminInfoEnabled.contains(playerId);
    }

    public void setAdminInfo(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled) {
            adminInfoEnabled.add(playerId);
        } else {
            adminInfoEnabled.remove(playerId);
        }
    }

    public boolean isBreakOthersActive(UUID playerId) {
        return playerId != null && breakOthersEnabled.contains(playerId);
    }

    public void setBreakOthers(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled) {
            breakOthersEnabled.add(playerId);
        } else {
            breakOthersEnabled.remove(playerId);
        }
    }
}
