package com.lincoln.itemshops;

import com.lincoln.itemshops.command.ItemShopsCommand;
import com.lincoln.itemshops.command.ShopHelpCommand;
import com.lincoln.itemshops.data.ShopStorage;
import com.lincoln.itemshops.gui.ChatAmountCapture;
import com.lincoln.itemshops.listener.*;
import com.lincoln.itemshops.manager.ShopManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class ItemShopsPlugin extends JavaPlugin {

    private static ItemShopsPlugin instance;

    private ShopManager shopManager;
    private ShopStorage storage;

    private com.lincoln.itemshops.vault.VaultManager vaultManager;
    public com.lincoln.itemshops.vault.VaultManager vault(){ return vaultManager; }


    public static ItemShopsPlugin get() { return instance; }

    private com.lincoln.itemshops.region.MarketRegionManager marketManager;
    public com.lincoln.itemshops.region.MarketRegionManager market(){ return marketManager; }


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
        vaultManager = new com.lincoln.itemshops.vault.VaultManager(this);
        marketManager = new com.lincoln.itemshops.region.MarketRegionManager(this);



        
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
        var vaultCmd = new com.lincoln.itemshops.command.VaultCommand(this);
        if (getCommand("shopvault") != null) {
            getCommand("shopvault").setExecutor(vaultCmd);
            getCommand("shopvault").setTabCompleter(vaultCmd);
        }
        var marketCmd = new com.lincoln.itemshops.command.MarketCommand(this);
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

        storage.saveAll(shopManager);
        storage.loadAll(shopManager);

        int pruned = shopManager.pruneInvalidShops();
        getLogger().info("Reload complete. Shops: " + shopManager.size() + " (pruned " + pruned + ").");
    }
}
