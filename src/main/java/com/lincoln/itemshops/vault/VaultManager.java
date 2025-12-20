package com.lincoln.itemshops.vault;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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

    public VaultManager(ItemShopsPlugin plugin){ this.plugin = plugin; }

    public void deposit(UUID owner, ItemStack template, int amount) {
        long exp = Instant.now().getEpochSecond() + RETENTION_SECONDS;
        List<VaultEntry> list = entries.computeIfAbsent(owner, k -> new ArrayList<>());
        
        for (VaultEntry ve : list) {
            if (ItemUtils.isSameItem(ve.item, template)) {
                
                ve.amount += amount;
                ve.expiresAt = Math.min(ve.expiresAt, exp);
                return;
            }
        }
        list.add(new VaultEntry(owner, template.clone(), amount, exp));
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
        return tak;
    }

    public void withdrawAll(UUID owner, Player p) {
        purgeExpired(owner);
        List<VaultEntry> list = entries.get(owner);
        if (list == null) return;
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
        }
    }

    public void purgeExpired(UUID owner) {
        List<VaultEntry> list = entries.get(owner);
        if (list == null) return;
        long now = Instant.now().getEpochSecond();
        list.removeIf(v -> v.expiresAt <= now);
        if (list.isEmpty()) entries.remove(owner);
    }
}
