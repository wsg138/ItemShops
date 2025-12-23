package dev.enthusia.itemshops.command;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.util.ItemUtils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class ShopHelpCommand implements CommandExecutor, TabCompleter {

    private final ItemShopsPlugin plugin;

    public ShopHelpCommand(ItemShopsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        
        if (!(sender instanceof Player p)) {
            sender.sendMessage("[ItemShops] Tutorial:");
            sender.sendMessage("  1) Create: Sneak + Left-Click a sign attached to a chest/barrel.");
            sender.sendMessage("  2) Choose: Put your GIVE and GET templates in the GUI, confirm.");
            sender.sendMessage("  3) Buy: Right-click the sign → purchase menu (1, custom, or max).");
            sender.sendMessage("  4) Edit/Delete: Left-click your shop sign (or use /shops edit).");
            sender.sendMessage("  5) Search: /shops search any <item>  (tab-completes items).");
            sender.sendMessage("  6) Trust: /shops trust <player>  (lets them restock).");
            return true;
        }

        
        p.sendMessage(ItemUtils.colored("&6&lItemShops &7— quick tutorial"));

        
        p.spigot().sendMessage(new ComponentBuilder("1) Create a shop: ")
                .color(ChatColor.GOLD)
                .append("Sneak + Left-Click").color(ChatColor.AQUA)
                .append(" a ").color(ChatColor.GRAY)
                .append("sign").color(ChatColor.WHITE)
                .append(" attached to a ").color(ChatColor.GRAY)
                .append("chest/barrel").color(ChatColor.WHITE)
                .append(".").color(ChatColor.GRAY)
                .create());

        
        p.spigot().sendMessage(new ComponentBuilder("2) In the GUI, put your ")
                .color(ChatColor.GOLD)
                .append("GIVE").color(ChatColor.GREEN)
                .append(" (what buyers receive) and ").color(ChatColor.GRAY)
                .append("GET").color(ChatColor.YELLOW)
                .append(" (what buyers pay) templates, then ").color(ChatColor.GRAY)
                .append("Confirm").color(ChatColor.GREEN)
                .append(".").color(ChatColor.GRAY)
                .create());

        
        p.spigot().sendMessage(new ComponentBuilder("3) Buyers ")
                .color(ChatColor.GOLD)
                .append("Right-Click").color(ChatColor.AQUA)
                .append(" the sign → ").color(ChatColor.GRAY)
                .append("Purchase 1").color(ChatColor.GREEN)
                .append(", ").color(ChatColor.DARK_GRAY)
                .append("Custom").color(ChatColor.AQUA)
                .append(", or ").color(ChatColor.DARK_GRAY)
                .append("Max").color(ChatColor.YELLOW)
                .append(".").color(ChatColor.GRAY)
                .create());

        
        TextComponent editClickable = new TextComponent("Open Your Shops");
        editClickable.setColor(ChatColor.GREEN);
        editClickable.setUnderlined(true);
        editClickable.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/shops edit"));
        editClickable.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Click to open your shops").color(ChatColor.GRAY).create()));

        p.spigot().sendMessage(new ComponentBuilder("4) Edit/Delete: ")
                .color(ChatColor.GOLD)
                .append("Left-Click your shop sign").color(ChatColor.AQUA)
                .append(" (owner), or use ").color(ChatColor.GRAY)
                .append(editClickable)
                .append(".").color(ChatColor.GRAY)
                .create());

        
        TextComponent searchAny = new TextComponent("/shops search any diamond");
        searchAny.setColor(ChatColor.YELLOW);
        searchAny.setUnderlined(true);
        searchAny.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/shops search any diamond"));
        searchAny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Find shops trading diamonds (tab completes items)").color(ChatColor.GRAY).create()));

        TextComponent searchBuy = new TextComponent("/shops search buy emerald");
        searchBuy.setColor(ChatColor.YELLOW);
        searchBuy.setUnderlined(true);
        searchBuy.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/shops search buy emerald"));
        searchBuy.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Shops buying emeralds").color(ChatColor.GRAY).create()));

        p.spigot().sendMessage(new ComponentBuilder("5) Search: ")
                .color(ChatColor.GOLD)
                .append("Use ").color(ChatColor.GRAY)
                .append(searchAny)
                .append(" or ").color(ChatColor.DARK_GRAY)
                .append(searchBuy)
                .append(".").color(ChatColor.GRAY)
                .create());

        
        TextComponent trustSuggest = new TextComponent("/shops trust <player>");
        trustSuggest.setColor(ChatColor.YELLOW);
        trustSuggest.setUnderlined(true);
        trustSuggest.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/shops trust "));
        trustSuggest.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Tab-complete a player to trust for restocking").color(ChatColor.GRAY).create()));

        p.spigot().sendMessage(new ComponentBuilder("6) Trust helpers: ")
                .color(ChatColor.GOLD)
                .append(trustSuggest)
                .append(".").color(ChatColor.GRAY)
                .create());

        
        p.sendMessage(ItemUtils.colored("&8Tip: the sign header shows &aGREEN &8when in stock and &cRED &8when out."));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList(); 
    }
}
