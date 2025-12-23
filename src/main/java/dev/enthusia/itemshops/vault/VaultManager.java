package dev.enthusia.itemshops.vault;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VaultManager {
    public static final long RETENTION_SECONDS = 7L * 24L * 60L * 60L;

    public static final class VaultEntry {
        public final UUID owner;
        public final ItemStack item; 
        public int amount;           
        public long expiresAt;       

        public VaultEntry(UUID owner, ItemStack item, int amount, long expiresAt) {
            this.owner = owner; this.item = item; this.amount = amount; this.expiresAt = expiresAt;
        }
    }

    private final ItemShopsPlugin plugin;
    
    private final Map<UUID, List<VaultEntry>> entries = new ConcurrentHashMap<>();
    private final File file;
    private FileConfiguration cfg;
    private BukkitTask pendingSaveTask;
    private static final long SAVE_DEBOUNCE_TICKS = 40L;

    public VaultManager(ItemShopsPlugin plugin){
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vault.yml");
        ensureFile();
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }

    public void deposit(UUID owner, ItemStack template, int amount) {
        long exp = Instant.now().getEpochSecond() + RETENTION_SECONDS;
        List<VaultEntry> list = entries.computeIfAbsent(owner, k -> new ArrayList<>());
        
        for (VaultEntry ve : list) {
            if (ItemUtils.isSameItem(ve.item, template)) {
                
                ve.amount += amount;
                ve.expiresAt = Math.min(ve.expiresAt, exp);
                saveAsync();
                return;
            }
        }
        list.add(new VaultEntry(owner, template.clone(), amount, exp));
        saveAsync();
    }

    public List<VaultEntry> list(UUID owner) {
        purgeExpired(owner);
        return new ArrayList<>(entries.getOrDefault(owner, Collections.emptyList()));
    }

    public int withdraw(UUID owner, int index, int amount) {
        purgeExpired(owner);
        List<VaultEntry> list = entries.get(owner);
        if (list == null || index < 0 || index >= list.size()) return 0;
        VaultEntry ve = list.get(index);
        int tak = Math.min(amount, ve.amount);
        ve.amount -= tak;
        if (ve.amount <= 0) list.remove(index);
        if (tak > 0) saveAsync();
        return tak;
    }

    public void withdrawAll(UUID owner, Player p) {
        purgeExpired(owner);
        List<VaultEntry> list = entries.get(owner);
        if (list == null) return;
        boolean changed = false;
        for (int i=list.size()-1;i>=0;i--) {
            VaultEntry ve = list.get(i);
            int left = ve.amount;
            if (left <= 0) { list.remove(i); continue; }
            int maxFitTrades = (int) ItemUtils.capacityTrades(p.getInventory(), ve.item, left);
            if (maxFitTrades <= 0) continue;
            int toGive = maxFitTrades;
            ItemUtils.addExact(p.getInventory(), ve.item, toGive);
            ve.amount -= toGive;
            if (ve.amount <= 0) list.remove(i);
            changed = true;
        }
        if (changed) saveAsync();
    }

    public void purgeExpired(UUID owner) {
        List<VaultEntry> list = entries.get(owner);
        if (list == null) return;
        long now = Instant.now().getEpochSecond();
        boolean changed = list.removeIf(v -> v.expiresAt <= now);
        if (list.isEmpty()) entries.remove(owner);
        if (changed) saveAsync();
    }

    public void saveNow() { saveAll(); }

    private void ensureFile() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed creating vault.yml: " + e.getMessage());
            }
        }
    }

    private void loadAll() {
        entries.clear();
        var root = cfg.getConfigurationSection("vault");
        if (root == null) return;
        long now = Instant.now().getEpochSecond();
        for (String key : root.getKeys(false)) {
            UUID owner;
            try { owner = UUID.fromString(key); }
            catch (Exception ex) { continue; }
            List<Map<?,?>> list = cfg.getMapList("vault." + key);
            if (list == null || list.isEmpty()) continue;
            List<VaultEntry> out = new ArrayList<>();
            for (Map<?,?> m : list) {
                try {
                    Object itemObj = m.get("item");
                    if (!(itemObj instanceof ItemStack item)) continue;
                    Object amountObj = m.get("amount");
                    Object expiresObj = m.get("expiresAt");
                    if (!(amountObj instanceof Number) || !(expiresObj instanceof Number)) continue;
                    int amount = ((Number) amountObj).intValue();
                    long expiresAt = ((Number) expiresObj).longValue();
                    if (amount <= 0 || expiresAt <= now) continue;
                    out.add(new VaultEntry(owner, item, amount, expiresAt));
                } catch (Exception ignored) {}
            }
            if (!out.isEmpty()) entries.put(owner, out);
        }
    }

    private void saveAll() {
        FileConfiguration out = new YamlConfiguration();
        for (var entry : entries.entrySet()) {
            String key = entry.getKey().toString();
            List<Map<String,Object>> list = new ArrayList<>();
            for (VaultEntry ve : entry.getValue()) {
                Map<String,Object> m = new HashMap<>();
                m.put("item", ve.item);
                m.put("amount", ve.amount);
                m.put("expiresAt", ve.expiresAt);
                list.add(m);
            }
            out.set("vault." + key, list);
        }
        try {
            out.save(file);
            cfg = out;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed saving vault.yml: " + e.getMessage());
        }
    }

    private void saveAsync() {
        if (pendingSaveTask != null) pendingSaveTask.cancel();
        pendingSaveTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingSaveTask = null;
            saveAll();
        }, SAVE_DEBOUNCE_TICKS);
    }
}
