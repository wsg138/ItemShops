package dev.enthusia.itemshops;

import dev.enthusia.itemshops.command.ItemShopsCommand;
import dev.enthusia.itemshops.command.ShopHelpCommand;
import dev.enthusia.itemshops.analytics.ShopAnalyticsListener;
import dev.enthusia.itemshops.analytics.ShopAnalyticsStore;
import dev.enthusia.itemshops.analytics.plan.AnalyticsIntegration;
import dev.enthusia.itemshops.analytics.plan.PlanAnalyticsHook;
import dev.enthusia.itemshops.config.ConfigMigrator;
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
import dev.enthusia.itemshops.service.ShopAuditService;
import dev.enthusia.itemshops.service.ShopRegistry;
import dev.enthusia.itemshops.service.ShopSignService;
import dev.enthusia.itemshops.service.ShopTradeService;
import dev.enthusia.itemshops.util.PerformanceCounters;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ItemShopsPlugin extends JavaPlugin {

    private static ItemShopsPlugin instance;

    private ShopManager shopManager;
    private ShopStorage storage;
    private ItemShopsConfig pluginConfig;
    private dev.enthusia.itemshops.vault.VaultManager vaultManager;
    private dev.enthusia.itemshops.region.MarketRegionManager marketManager;
    private GuildShopIntegration guildShopIntegration;
    private ShopTradeService shopTradeService;
    private ShopAnalyticsStore analyticsStore;
    private AnalyticsIntegration planAnalytics;
    private ShopAuditService shopAuditService;
    private PerformanceCounters performanceCounters;
    private FileConfiguration messages;

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
        new ConfigMigrator(this).migrateStartupFiles();
        reloadCachedMessages();

        pluginConfig = new ItemShopsConfig(this);
        performanceCounters = new PerformanceCounters(this);
        analyticsStore = new ShopAnalyticsStore(this);
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
        shopAuditService = new ShopAuditService(this, shopManager);
        shopAuditService.restart();
        performanceCounters.restartLogger();

        getServer().getPluginManager().registerEvents(new SignShopListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ContainerStockListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new ContainerAccessListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new ExplodeCleanupListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new HopperControlListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new ShopAnalyticsListener(this, analyticsStore), this);

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
        hookPlanAnalytics();
        getLogger().info("ItemShops enabled. Loaded " + shopManager.size() + " shops.");
    }

    @Override
    public void onDisable() {
        if (shopAuditService != null) {
            shopAuditService.stop();
        }
        if (performanceCounters != null) {
            performanceCounters.stopLogger();
        }
        try {
            if (shopManager != null) {
                shopManager.shutdown();
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to save shops during disable.", e);
        }
        if (vaultManager != null) {
            vaultManager.saveNow();
        }
        if (planAnalytics != null) {
            planAnalytics.shutdown();
            planAnalytics = null;
        }
        if (analyticsStore != null) {
            analyticsStore.shutdown();
        }
    }

    public FileConfiguration messages() {
        return messages;
    }

    public ShopManager shops() {
        return shopManager;
    }

    public ShopStorage storage() {
        return storage;
    }

    public ShopAnalyticsStore analytics() {
        return analyticsStore;
    }

    public ShopAuditService audit() {
        return shopAuditService;
    }

    public PerformanceCounters performance() {
        return performanceCounters;
    }

    public ItemShopsConfig pluginConfig() {
        return pluginConfig;
    }

    private void reloadCachedMessages() {
        YamlLoader.mergeDefaults(this, "messages.yml");
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
    }

    private boolean ensureConfigDefaults() {
        boolean changed = YamlLoader.mergeDefaults(this, "config.yml");
        if (changed) {
            getLogger().info("config.yml was missing entries; added defaults from the jar.");
        }
        return changed;
    }

    public void reloadAll() {
        new ConfigMigrator(this).migrateStartupFiles();
        reloadConfig();
        if (pluginConfig != null) {
            pluginConfig.reload();
        }
        reloadCachedMessages();
        if (shopManager != null) {
            shopManager.saveNow();
        }
        storage.reloadFile();
        if (shopManager != null) {
            shopManager.loadFromStorage();
            getLogger().info("Reloaded shop storage from disk after saving current in-memory state.");
        }
        if (analyticsStore != null) {
            analyticsStore.reload();
        }
        if (marketManager != null) {
            marketManager.reload();
        }

        shopManager.startSignUpdateScheduler();
        if (shopAuditService != null) {
            shopAuditService.restart();
        }
        if (performanceCounters != null) {
            performanceCounters.restartLogger();
        }

        hookPlanAnalytics();
        getLogger().info("Reload complete. Shops: " + shopManager.size() + ".");
    }

    private void hookPlanAnalytics() {
        if (!pluginConfig.planAnalyticsEnabled()) {
            if (planAnalytics != null) {
                planAnalytics.shutdown();
                planAnalytics = null;
            }
            return;
        }
        if (planAnalytics != null) {
            return;
        }
        if (getServer().getPluginManager().getPlugin("Plan") == null) {
            getLogger().fine("Plan is not installed; ItemShops Plan analytics integration skipped.");
            return;
        }
        try {
            PlanAnalyticsHook hook = new PlanAnalyticsHook(this, analyticsStore);
            hook.hookIntoPlan();
            planAnalytics = hook;
        } catch (NoClassDefFoundError e) {
            getLogger().fine("Plan API is unavailable; ItemShops Plan analytics integration skipped.");
        } catch (Throwable t) {
            getLogger().warning("Failed to enable ItemShops Plan analytics integration: " + t.getMessage());
        }
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
