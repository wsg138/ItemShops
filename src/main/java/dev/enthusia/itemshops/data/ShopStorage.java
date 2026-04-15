package dev.enthusia.itemshops.data;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.config.ItemShopsConfig;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.region.MarketRegionManager;
import dev.enthusia.itemshops.util.Pos;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class ShopStorage {
    private static final long SAVE_DEBOUNCE_TICKS = 40L;

    private final ItemShopsPlugin plugin;
    private final ItemShopsConfig pluginConfig;
    private final MarketRegionManager marketRegionManager;

    private File file;
    private FileConfiguration cfg;
    private BukkitTask pendingSaveTask;
    private BukkitTask pendingAsyncWriteTask;

    public ShopStorage(ItemShopsPlugin plugin, ItemShopsConfig pluginConfig, MarketRegionManager marketRegionManager) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.marketRegionManager = marketRegionManager;
        resetFileHandle();
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void reloadFile() {
        resetFileHandle();
        this.cfg = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void saveAll(Collection<Shop> shops) {
        cancelPendingSaves();
        writeSnapshot(buildSnapshot(shops));
    }

    public synchronized void saveAsync(Collection<Shop> shops) {
        if (pendingSaveTask != null) pendingSaveTask.cancel();
        List<StoredShop> snapshot = buildSnapshot(shops);
        pendingSaveTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            synchronized (ShopStorage.this) {
                pendingSaveTask = null;
                if (pendingAsyncWriteTask != null) pendingAsyncWriteTask.cancel();
                pendingAsyncWriteTask = plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    synchronized (ShopStorage.this) {
                        pendingAsyncWriteTask = null;
                        writeSnapshot(snapshot);
                    }
                });
            }
        }, SAVE_DEBOUNCE_TICKS);
    }

    public synchronized List<Shop> loadAll() {
        List<Shop> shops = new ArrayList<>();
        ConfigurationSection root = cfg.getConfigurationSection("shops");
        if (root == null) return shops;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            Shop shop = deserializeShop(section);
            if (shop != null) shops.add(shop);
        }
        return shops;
    }

    public synchronized Shop loadOneBySign(Pos signPos) {
        ConfigurationSection root = cfg.getConfigurationSection("shops");
        if (root == null) return null;

        for (String key : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(key);
            if (section == null) continue;
            ConfigurationSection signSection = section.getConfigurationSection("sign");
            if (signSection == null) continue;

            String world = signSection.getString("world", "world");
            int x = signSection.getInt("x");
            int y = signSection.getInt("y");
            int z = signSection.getInt("z");
            if (!signPos.world.equals(world) || signPos.x != x || signPos.y != y || signPos.z != z) {
                continue;
            }
            return deserializeShop(section);
        }
        return null;
    }

    public synchronized void shutdownAndSave(Collection<Shop> shops) {
        cancelPendingSaves();
        writeSnapshot(buildSnapshot(shops));
    }

    private void cancelPendingSaves() {
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
        if (pendingAsyncWriteTask != null) {
            pendingAsyncWriteTask.cancel();
            pendingAsyncWriteTask = null;
        }
    }

    private void resetFileHandle() {
        String path = pluginConfig.storageFileName();
        file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed creating shop storage file: " + e.getMessage());
            }
        }
    }

    private List<StoredShop> buildSnapshot(Collection<Shop> shops) {
        List<StoredShop> snapshot = new ArrayList<>();
        for (Shop shop : shops) {
            snapshot.add(StoredShop.from(shop));
        }
        return snapshot;
    }

    private void writeSnapshot(List<StoredShop> snapshot) {
        FileConfiguration out = new YamlConfiguration();
        int index = 0;
        for (StoredShop shop : snapshot) {
            String key = "shops." + index++;
            out.set(key + ".id", shop.id.toString());
            out.set(key + ".sign.world", shop.sign.world);
            out.set(key + ".sign.x", shop.sign.x);
            out.set(key + ".sign.y", shop.sign.y);
            out.set(key + ".sign.z", shop.sign.z);
            out.set(key + ".container.world", shop.container.world);
            out.set(key + ".container.x", shop.container.x);
            out.set(key + ".container.y", shop.container.y);
            out.set(key + ".container.z", shop.container.z);
            out.set(key + ".owner", shop.owner.toString());
            out.set(key + ".sell", shop.sell);
            out.set(key + ".cost", shop.cost);
            out.set(key + ".trusted", shop.trusted.stream().map(UUID::toString).toList());
            out.set(key + ".hopper.allow_in", shop.hopperAllowIn);
            out.set(key + ".hopper.allow_out", shop.hopperAllowOut);
            out.set(key + ".search.enabled", shop.searchEnabled);
            out.set(key + ".freeze.enabled", shop.frozen);
            out.set(key + ".freeze.until_ms", shop.frozenUntilMs);
        }

        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            out.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            cfg = out;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed saving shop storage: " + e.getMessage());
        } finally {
            if (tmp.exists() && !tmp.equals(file)) {
                tmp.delete();
            }
        }
    }

    private Shop deserializeShop(ConfigurationSection section) {
        ConfigurationSection signSection = section.getConfigurationSection("sign");
        ConfigurationSection containerSection = section.getConfigurationSection("container");
        if (signSection == null || containerSection == null) return null;

        UUID id;
        try {
            id = UUID.fromString(section.getString("id", UUID.randomUUID().toString()));
        } catch (Exception ex) {
            id = UUID.randomUUID();
        }

        Pos sign = new Pos(
                signSection.getString("world", "world"),
                signSection.getInt("x"),
                signSection.getInt("y"),
                signSection.getInt("z")
        );
        Pos container = new Pos(
                containerSection.getString("world", "world"),
                containerSection.getInt("x"),
                containerSection.getInt("y"),
                containerSection.getInt("z")
        );

        UUID owner;
        try {
            owner = UUID.fromString(section.getString("owner"));
        } catch (Exception ex) {
            return null;
        }

        ItemStack sell = section.getItemStack("sell");
        ItemStack cost = section.getItemStack("cost");
        if (sell == null || cost == null) return null;

        Shop shop = new Shop(id, sign, container, owner, sell, cost);
        for (String raw : section.getStringList("trusted")) {
            try {
                shop.addTrusted(UUID.fromString(raw));
            } catch (Exception ignored) {
            }
        }

        shop.setHopperAllowIn(section.getBoolean("hopper.allow_in", pluginConfig.defaultHopperAllowIn()));
        shop.setHopperAllowOut(section.getBoolean("hopper.allow_out", pluginConfig.defaultHopperAllowOut()));

        boolean defaultSearch = marketRegionManager.isInMarket(container);
        shop.setSearchEnabled(section.getBoolean("search.enabled", defaultSearch));

        boolean frozen = section.getBoolean("freeze.enabled", false);
        long untilMs = section.getLong("freeze.until_ms", 0L);
        if (frozen) {
            if (untilMs > 0) shop.freezeUntil(untilMs);
            else shop.freezeIndefinitely();
        }
        return shop;
    }

    private static final class StoredShop {
        private final UUID id;
        private final Pos sign;
        private final Pos container;
        private final UUID owner;
        private final ItemStack sell;
        private final ItemStack cost;
        private final List<UUID> trusted;
        private final boolean hopperAllowIn;
        private final boolean hopperAllowOut;
        private final boolean searchEnabled;
        private final boolean frozen;
        private final long frozenUntilMs;

        private StoredShop(UUID id,
                           Pos sign,
                           Pos container,
                           UUID owner,
                           ItemStack sell,
                           ItemStack cost,
                           List<UUID> trusted,
                           boolean hopperAllowIn,
                           boolean hopperAllowOut,
                           boolean searchEnabled,
                           boolean frozen,
                           long frozenUntilMs) {
            this.id = id;
            this.sign = sign;
            this.container = container;
            this.owner = owner;
            this.sell = sell;
            this.cost = cost;
            this.trusted = trusted;
            this.hopperAllowIn = hopperAllowIn;
            this.hopperAllowOut = hopperAllowOut;
            this.searchEnabled = searchEnabled;
            this.frozen = frozen;
            this.frozenUntilMs = frozenUntilMs;
        }

        private static StoredShop from(Shop shop) {
            return new StoredShop(
                    shop.id(),
                    shop.sign(),
                    shop.container(),
                    shop.owner(),
                    shop.sell() == null ? null : shop.sell().clone(),
                    shop.cost() == null ? null : shop.cost().clone(),
                    new ArrayList<>(shop.trusted()),
                    shop.isHopperAllowIn(),
                    shop.isHopperAllowOut(),
                    shop.isSearchEnabled(),
                    shop.isFrozen(),
                    shop.frozenUntilMs()
            );
        }
    }
}
