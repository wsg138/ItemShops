package com.lincoln.itemshops.data;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.Pos;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ShopStorage {
    private final ItemShopsPlugin plugin;
    private File file;
    private FileConfiguration cfg;

    private BukkitTask pendingSaveTask;
    private static final long SAVE_DEBOUNCE_TICKS = 40L;

    public ShopStorage(ItemShopsPlugin plugin) {
        this.plugin = plugin;
        String path = plugin.getConfig().getString("storage.file","shops.yml");
        this.file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed creating shops.yml: " + e.getMessage());
            }
        }
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public void reloadFile() { this.cfg = YamlConfiguration.loadConfiguration(file); }

    public void saveAll(ShopManager mgr) {
        cfg.set("shops", null);
        int i = 0;
        for (Shop s : mgr.all()) {
            String k = "shops."+i++;
            cfg.set(k+".sign.world", s.sign().world);
            cfg.set(k+".sign.x", s.sign().x);
            cfg.set(k+".sign.y", s.sign().y);
            cfg.set(k+".sign.z", s.sign().z);

            cfg.set(k+".container.world", s.container().world);
            cfg.set(k+".container.x", s.container().x);
            cfg.set(k+".container.y", s.container().y);
            cfg.set(k+".container.z", s.container().z);

            cfg.set(k+".owner", s.owner().toString());
            cfg.set(k+".sell", s.sell());
            cfg.set(k+".cost", s.cost());

            
            List<String> trust = new ArrayList<>();
            for (UUID u : s.trusted()) trust.add(u.toString());
            cfg.set(k+".trusted", trust);

            
            cfg.set(k+".hopper.allow_in", s.isHopperAllowIn());
            cfg.set(k+".hopper.allow_out", s.isHopperAllowOut());

            
            cfg.set(k+".search.enabled", s.isSearchEnabled());
        }
        try { cfg.save(file); } catch (IOException e) { plugin.getLogger().severe("Failed saving shops.yml: "+e.getMessage()); }
    }

    public void saveAsync(ShopManager mgr) {
        if (pendingSaveTask != null) pendingSaveTask.cancel();
        pendingSaveTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSaveTask = null;
            saveAll(mgr);
        }, SAVE_DEBOUNCE_TICKS);
    }

    public void loadAll(ShopManager mgr) {
        mgr.all().clear();
        ConfigurationSection root = cfg.getConfigurationSection("shops");
        if (root == null) return;

        boolean defIn  = plugin.getConfig().getBoolean("hoppers.default-allow-in",  false);
        boolean defOut = plugin.getConfig().getBoolean("hoppers.default-allow-out", false);

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;

            Pos sign = new Pos(
                    s.getConfigurationSection("sign").getString("world","world"),
                    s.getConfigurationSection("sign").getInt("x"),
                    s.getConfigurationSection("sign").getInt("y"),
                    s.getConfigurationSection("sign").getInt("z")
            );
            Pos cont = new Pos(
                    s.getConfigurationSection("container").getString("world","world"),
                    s.getConfigurationSection("container").getInt("x"),
                    s.getConfigurationSection("container").getInt("y"),
                    s.getConfigurationSection("container").getInt("z")
            );

            UUID owner;
            try { owner = UUID.fromString(s.getString("owner")); }
            catch (Exception ex) { continue; }

            ItemStack sell = s.getItemStack("sell");
            ItemStack cost = s.getItemStack("cost");
            if (sell == null || cost == null) continue;

            Shop shop = new Shop(sign, cont, owner, sell, cost);

            for (String us : s.getStringList("trusted")) {
                try { shop.addTrusted(UUID.fromString(us)); } catch (Exception ignored) {}
            }
            shop.setHopperAllowIn(s.getBoolean("hopper.allow_in", defIn));
            shop.setHopperAllowOut(s.getBoolean("hopper.allow_out", defOut));

            
            boolean defSearch = plugin.market().isInMarket(cont);
            shop.setSearchEnabled(s.getBoolean("search.enabled", defSearch));

            
            mgr.indexExisting(shop);
        }
    }

    
    public Shop loadOneBySign(Pos signPos) {
        
        ConfigurationSection root = cfg.getConfigurationSection("shops");
        if (root == null) return null;

        boolean defIn  = plugin.getConfig().getBoolean("hoppers.default-allow-in",  false);
        boolean defOut = plugin.getConfig().getBoolean("hoppers.default-allow-out", false);

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;

            String w = s.getConfigurationSection("sign").getString("world","world");
            int x = s.getConfigurationSection("sign").getInt("x");
            int y = s.getConfigurationSection("sign").getInt("y");
            int z = s.getConfigurationSection("sign").getInt("z");
            if (!signPos.world.equals(w) || signPos.x != x || signPos.y != y || signPos.z != z) continue;

            Pos cont = new Pos(
                    s.getConfigurationSection("container").getString("world","world"),
                    s.getConfigurationSection("container").getInt("x"),
                    s.getConfigurationSection("container").getInt("y"),
                    s.getConfigurationSection("container").getInt("z")
            );

            UUID owner;
            try { owner = UUID.fromString(s.getString("owner")); }
            catch (Exception ex) { return null; }

            ItemStack sell = s.getItemStack("sell");
            ItemStack cost = s.getItemStack("cost");
            if (sell == null || cost == null) return null;

            Shop shop = new Shop(signPos, cont, owner, sell, cost);
            for (String us : s.getStringList("trusted")) {
                try { shop.addTrusted(UUID.fromString(us)); } catch (Exception ignored) {}
            }
            shop.setHopperAllowIn(s.getBoolean("hopper.allow_in", defIn));
            shop.setHopperAllowOut(s.getBoolean("hopper.allow_out", defOut));

            boolean defSearch = plugin.market().isInMarket(cont);
            shop.setSearchEnabled(s.getBoolean("search.enabled", defSearch));
            return shop;
        }
        return null;
    }
}
