package dev.enthusia.itemshops;

import dev.enthusia.itemshops.command.ItemShopsCommand;
import dev.enthusia.itemshops.command.ShopHelpCommand;
import dev.enthusia.itemshops.data.ShopStorage;
import dev.enthusia.itemshops.gui.ChatAmountCapture;
import dev.enthusia.itemshops.listener.*;
import dev.enthusia.itemshops.manager.ShopManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemShopsPlugin extends JavaPlugin {

    private static ItemShopsPlugin instance;

    private ShopManager shopManager;
    private ShopStorage storage;

    private dev.enthusia.itemshops.vault.VaultManager vaultManager;
    public dev.enthusia.itemshops.vault.VaultManager vault(){ return vaultManager; }


    public static ItemShopsPlugin get() { return instance; }

    private dev.enthusia.itemshops.region.MarketRegionManager marketManager;
    public dev.enthusia.itemshops.region.MarketRegionManager market(){ return marketManager; }

    private final Map<UUID, Long> breakDeleteExpiry = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> adminViewEnabled = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> adminInfoEnabled = ConcurrentHashMap.newKeySet();
    private final java.util.Set<UUID> breakOthersEnabled = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        ensureConfigDefaults();
        saveResource("messages.yml", false);
        YamlLoader.mergeDefaults(this, "messages.yml");

        storage = new ShopStorage(this);
        shopManager = new ShopManager(this, storage);
        shopManager.startSignUpdateScheduler();

        
        getServer().getPluginManager().registerEvents(new SignShopListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new BlockProtectionListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new ContainerStockListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new ContainerAccessListener(this, shopManager), this);
        getServer().getPluginManager().registerEvents(new ExplodeCleanupListener(shopManager), this);
        getServer().getPluginManager().registerEvents(new HopperControlListener(shopManager), this);
        vaultManager = new dev.enthusia.itemshops.vault.VaultManager(this);
        marketManager = new dev.enthusia.itemshops.region.MarketRegionManager(this);



        
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



        
        storage.loadAll(shopManager);
        int pruned = shopManager.pruneInvalidShops();
        getLogger().info("ItemShops enabled. Loaded " + shopManager.size() + " shops (pruned " + pruned + ").");
    }

    @Override
    public void onDisable() {
        try { storage.saveAll(shopManager); }
        catch (Exception e) {
            getLogger().severe("Failed to save shops: " + e.getMessage());
            e.printStackTrace();
        }
        if (vaultManager != null) vaultManager.saveNow();
        shopManager.stopSignUpdateScheduler();
    }

    
    public FileConfiguration messages() { return YamlLoader.get(this, "messages.yml"); }

    
    public ShopManager shops() { return shopManager; }

    
    public ShopStorage storage() { return storage; }

    
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
        YamlLoader.mergeDefaults(this, "messages.yml");
        YamlLoader.reload(this, "messages.yml");
        storage.reloadFile();
        if (marketManager != null) marketManager.reload();

        storage.saveAll(shopManager);
        storage.loadAll(shopManager);

        int pruned = shopManager.pruneInvalidShops();
        getLogger().info("Reload complete. Shops: " + shopManager.size() + " (pruned " + pruned + ").");
    }

    public void enableBreakDelete(UUID playerId, long durationMs) {
        if (playerId == null) return;
        breakDeleteExpiry.put(playerId, System.currentTimeMillis() + Math.max(0L, durationMs));
    }

    public void disableBreakDelete(UUID playerId) {
        if (playerId == null) return;
        breakDeleteExpiry.remove(playerId);
    }

    public boolean isBreakDeleteActive(UUID playerId) {
        if (playerId == null) return false;
        Long exp = breakDeleteExpiry.get(playerId);
        if (exp == null) return false;
        if (exp < System.currentTimeMillis()) {
            breakDeleteExpiry.remove(playerId);
            return false;
        }
        return true;
    }

    public long breakDeleteRemainingMs(UUID playerId) {
        if (playerId == null) return 0L;
        Long exp = breakDeleteExpiry.get(playerId);
        if (exp == null) return 0L;
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
        if (playerId == null) return;
        if (enabled) adminViewEnabled.add(playerId);
        else adminViewEnabled.remove(playerId);
    }

    public boolean isAdminInfoActive(UUID playerId) {
        return playerId != null && adminInfoEnabled.contains(playerId);
    }
    public void setAdminInfo(UUID playerId, boolean enabled) {
        if (playerId == null) return;
        if (enabled) adminInfoEnabled.add(playerId);
        else adminInfoEnabled.remove(playerId);
    }

    public boolean isBreakOthersActive(UUID playerId) {
        return playerId != null && breakOthersEnabled.contains(playerId);
    }
    public void setBreakOthers(UUID playerId, boolean enabled) {
        if (playerId == null) return;
        if (enabled) breakOthersEnabled.add(playerId);
        else breakOthersEnabled.remove(playerId);
    }
}
