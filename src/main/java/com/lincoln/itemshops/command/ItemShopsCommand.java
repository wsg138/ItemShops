package com.lincoln.itemshops.command;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.gui.BulkTrustMenu;
import com.lincoln.itemshops.gui.OwnedShopsMenu;
import com.lincoln.itemshops.gui.SearchResultsMenu;
import com.lincoln.itemshops.manager.ShopManager;
import com.lincoln.itemshops.model.Shop;
import com.lincoln.itemshops.util.ItemUtils;
import com.lincoln.itemshops.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public final class ItemShopsCommand implements CommandExecutor, TabCompleter {
    private final ShopManager mgr;

    public ItemShopsCommand(ShopManager mgr){ this.mgr = mgr; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ItemUtils.colored("&e/itemshops reload"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops search <item> [sell|buy|any] [page]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops trust <player> [all|menu]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops untrust <player> [all]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops list"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops edit"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops delete"));
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            ItemShopsPlugin.get().reloadAll();
            sender.sendMessage(ItemUtils.colored("&aReloaded ItemShops."));
            return true;
        }

        if ("search".equalsIgnoreCase(args[0])) {
            
            mgr.pruneInvalidShops();

            if (args.length < 2) { sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "search.usage")); return true; }
            String query = args[1];
            String mode = args.length >=3 ? args[2].toLowerCase(Locale.ROOT) : "any";
            int page = 0;
            if (args.length >=4) try { page = Math.max(0, Integer.parseInt(args[3]) - 1); } catch (NumberFormatException ignored) {}

            int limit = 1000;
            var list = mgr.search(query, mode, limit+1);

            if (!(sender instanceof Player player)) {
                if (list.isEmpty()) { sender.sendMessage(Texts.fmt(ItemShopsPlugin.get().messages(), "search.none", "query", query)); return true; }
                int perPage = 10;
                int from = page * perPage, to = Math.min(list.size(), from + perPage);
                if (from >= list.size()) { sender.sendMessage(ItemUtils.colored("&7No more results.")); return true; }
                sender.sendMessage(Texts.fmt(ItemShopsPlugin.get().messages(),"search.header","count", (to-from), "query", query, "mode", mode));
                for (int i=from;i<to;i++) {
                    Shop s = list.get(i);
                    String owner = Optional.ofNullable(Bukkit.getOfflinePlayer(s.owner()).getName()).orElse("Unknown");
                    int trades = 0;
                    var loc = s.container().toLocation();
                    if (loc != null && loc.getBlock().getState() instanceof org.bukkit.block.Container cont) {
                        int stock = com.lincoln.itemshops.util.ItemUtils.countSimilar(cont.getInventory(), s.sell());
                        trades = stock / Math.max(1, s.sell().getAmount());
                    }
                    sender.sendMessage(Texts.fmt(ItemShopsPlugin.get().messages(), "search.line",
                            "world", s.sign().world, "x", s.sign().x, "y", s.sign().y, "z", s.sign().z,
                            "owner", owner,
                            "sell_amt", s.sell().getAmount(), "sell_name", com.lincoln.itemshops.util.ItemUtils.niceName(s.sell()),
                            "cost_amt", s.cost().getAmount(), "cost_name", com.lincoln.itemshops.util.ItemUtils.niceName(s.cost()),
                            "trades", trades));
                }
                sender.sendMessage(ItemUtils.colored("&7Page " + (page+1) + " of " + Math.max(1, (list.size()+perPage-1)/perPage)));
                return true;
            }

            
            new SearchResultsMenu(ItemShopsPlugin.get(), (Player) sender, list, query, mode).open();
            return true;
        }

        if ("trust".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops trust <player> [all|menu]")); return true; }
            String name = args[1];
            var op = Bukkit.getOfflinePlayer(name);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            String mode = args.length >=3 ? args[2].toLowerCase(Locale.ROOT) : "menu";
            if ("all".equals(mode)) {
                var list = mgr.ownedBy(p.getUniqueId());
                for (var s : list) { s.addTrusted(op.getUniqueId()); ItemShopsPlugin.get().shops().updateSign(s); }
                sender.sendMessage(ItemUtils.colored("&aTrusted &f"+name+"&a for all your shops."));
                return true;
            }
            new BulkTrustMenu(ItemShopsPlugin.get(), mgr, p, op.getUniqueId(), name).open();
            return true;
        }

        if ("untrust".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops untrust <player> [all]")); return true; }
            String name = args[1];
            var op = Bukkit.getOfflinePlayer(name);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            if (args.length >=3 && "all".equalsIgnoreCase(args[2])) {
                var list = mgr.ownedBy(p.getUniqueId());
                for (var s : list) { s.removeTrusted(op.getUniqueId()); ItemShopsPlugin.get().shops().updateSign(s); }
                sender.sendMessage(ItemUtils.colored("&eUntrusted &f"+name+"&e for all your shops."));
                return true;
            }
            new BulkTrustMenu(ItemShopsPlugin.get(), mgr, p, op.getUniqueId(), name).open();
            return true;
        }

        if ("list".equalsIgnoreCase(args[0])) {
            mgr.pruneInvalidShops();
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            var list = mgr.ownedBy(p.getUniqueId());
            if (list.isEmpty()) { p.sendMessage(ItemUtils.colored("&7You own no shops.")); return true; }
            p.sendMessage(ItemUtils.colored("&6Your shops ("+list.size()+")"));
            for (var s : list) {
                p.sendMessage(ItemUtils.colored("&7- &f"+s.sign().world+" "+s.sign().x+","+s.sign().y+","+s.sign().z+
                        " &8| &a"+ItemUtils.niceName(s.sell())+" x"+s.sell().getAmount()+" &7for &e"+ItemUtils.niceName(s.cost())+" x"+s.cost().getAmount()));
            }
            return true;
        }

        if ("edit".equalsIgnoreCase(args[0])) {
            mgr.pruneInvalidShops();
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            new OwnedShopsMenu(ItemShopsPlugin.get(), mgr, p).open();
            return true;
        }

        if ("delete".equalsIgnoreCase(args[0])) {
            mgr.pruneInvalidShops();
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            p.sendMessage(ItemUtils.colored("&eOpen a shop in /itemshops edit and use the &cDelete Shop &ebutton."));
            new OwnedShopsMenu(ItemShopsPlugin.get(), mgr, p).open();
            return true;
        }

        sender.sendMessage(ItemUtils.colored("&cUnknown subcommand."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("reload","search","trust","untrust","list","edit","delete");

        if ("search".equalsIgnoreCase(args[0])) {
            if (args.length == 2) {
                String prefix = args[1].toUpperCase(Locale.ROOT);
                return Arrays.stream(Material.values())
                        .filter(Material::isItem)
                        .map(Material::name)
                        .filter(n -> n.startsWith(prefix))
                        .limit(50)
                        .collect(Collectors.toList());
            }
            if (args.length == 3) return Arrays.asList("sell","buy","any");
            if (args.length == 4) return Arrays.asList("1","2","3","4","5");
        }

        
        if ((args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust")) && args.length == 2) {
            String pfx = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
        }

        if ("trust".equalsIgnoreCase(args[0]) && args.length == 3) return Arrays.asList("menu","all");
        if ("untrust".equalsIgnoreCase(args[0]) && args.length == 3) return Arrays.asList("all");
        return List.of();
    }
}
