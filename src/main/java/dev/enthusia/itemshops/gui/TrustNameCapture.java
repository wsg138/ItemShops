package dev.enthusia.itemshops.gui;

import dev.enthusia.itemshops.ItemShopsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class TrustNameCapture implements Listener {
    private static final Map<UUID, BiConsumer<UUID,String>> waiting = new ConcurrentHashMap<>();
    private static boolean registered = false;

    public static void register(JavaPlugin plugin) {
        if (!registered) {
            Bukkit.getPluginManager().registerEvents(new TrustNameCapture(), plugin);
            registered = true;
        }
    }

    
    public static void start(Player p, BiConsumer<UUID,String> consumer) {
        if (p == null) return;
        waiting.put(p.getUniqueId(), consumer);
    }

    



    public static void request(Player owner, String msg, Object unused) {
        if (owner == null) return;
        start(owner, (uuid, name) -> {}); 
        owner.sendMessage(color("&6[ItemShops]&r " + (msg != null ? msg : "Type a player name to trust.")));
        owner.sendMessage(color("&7• &fJava: &7Type the exact username"));
        owner.sendMessage(color("&7• &fBedrock: &7Gamertags can have spaces—type the full name"));
        owner.sendMessage(color("&7• Type &c'cancel'&7 to abort"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        BiConsumer<UUID,String> consumer = waiting.remove(e.getPlayer().getUniqueId());
        if (consumer == null) return; 

        e.setCancelled(true);
        String msg = e.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            consumer.accept(null, null);
            return;
        }

        
        Player online = findOnlineMatch(msg);
        if (online != null) {
            consumer.accept(online.getUniqueId(), online.getName());
            return;
        }

        
        OfflinePlayer op = Bukkit.getOfflinePlayer(msg);
        if (op != null && (op.getName() != null || op.hasPlayedBefore())) {
            consumer.accept(op.getUniqueId(), op.getName() != null ? op.getName() : msg);
            return;
        }

        
        consumer.accept(null, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        
        waiting.remove(e.getPlayer().getUniqueId());
    }

    

    private static Player findOnlineMatch(String input) {
        String normInput = normalizeName(input);
        Player exact = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            String norm = normalizeName(p.getName());
            if (norm.equalsIgnoreCase(normInput)) return p; 
            if (exact == null && p.getName().equalsIgnoreCase(input)) exact = p; 
        }
        if (exact != null) return exact;

        
        for (Player p : Bukkit.getOnlinePlayers()) {
            String norm = normalizeName(p.getName());
            if (norm.startsWith(normInput) || norm.contains(normInput)) return p;
        }
        return null;
    }

    





    private static String normalizeName(String s) {
        if (s == null) return "";
        String t = s.trim();
        
        while (!t.isEmpty() && (t.charAt(0) == '.' || t.charAt(0) == '*' || t.charAt(0) == '!' || t.charAt(0) == '_')) {
            t = t.substring(1);
        }
        
        return t.replace(" ", "").replace("_", "").toLowerCase();
    }

    private static String color(String s) {
        return s == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', s);
    }
}
