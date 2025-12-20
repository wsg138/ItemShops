package com.lincoln.itemshops.gui;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;






public final class ChatAmountCapture implements Listener {
    private static final Map<UUID, Entry> waiting = new ConcurrentHashMap<>();
    private static final long DEFAULT_TTL_MS = 30_000L;

    private static class Entry {
        final IntConsumer handler;
        final long expiresAt;
        Entry(IntConsumer handler, long expiresAt) { this.handler = handler; this.expiresAt = expiresAt; }
    }

    private ChatAmountCapture() {}

    
    public static void register(ItemShopsPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(new ChatAmountCapture(), plugin);
        
        Bukkit.getScheduler().runTaskTimer(plugin, ChatAmountCapture::cleanup, 200L, 200L);
    }

    
    public static void request(Player p, String prompt, IntConsumer handler) {
        p.sendMessage(ItemUtils.colored("&7" + prompt + " &8(Type a number or 'cancel')"));
        waiting.put(p.getUniqueId(), new Entry(handler, System.currentTimeMillis() + DEFAULT_TTL_MS));
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        waiting.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Entry en = waiting.remove(e.getPlayer().getUniqueId());
        if (en == null) return;
        e.setCancelled(true);

        String msg = e.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            e.getPlayer().sendMessage(ItemUtils.colored("&7Purchase cancelled."));
            return;
        }

        int n;
        try {
            n = Integer.parseInt(msg);
            if (n <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            e.getPlayer().sendMessage(ItemUtils.colored("&cPlease enter a positive whole number (or 'cancel')."));
            
            waiting.put(e.getPlayer().getUniqueId(), new Entry(en.handler, System.currentTimeMillis() + DEFAULT_TTL_MS));
            return;
        }

        
        Bukkit.getScheduler().runTask((Plugin) ItemShopsPlugin.get(), () -> en.handler.accept(n));
    }
}
