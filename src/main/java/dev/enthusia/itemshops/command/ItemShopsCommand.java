package dev.enthusia.itemshops.command;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.gui.BulkTrustMenu;
import dev.enthusia.itemshops.gui.DeleteShopsMenu;
import dev.enthusia.itemshops.gui.FreezeShopsMenu;
import dev.enthusia.itemshops.gui.OwnedShopsMenu;
import dev.enthusia.itemshops.gui.SearchResultsMenu;
import dev.enthusia.itemshops.gui.VaultAdminMenu;
import dev.enthusia.itemshops.manager.ShopManager;
import dev.enthusia.itemshops.model.Shop;
import dev.enthusia.itemshops.util.ItemUtils;
import dev.enthusia.itemshops.util.Texts;
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
            sender.sendMessage(ItemUtils.colored("&e/itemshops breakdelete [on|off|5m]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops adminview [on|off]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops info [on|off]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops breakothers [on|off]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops remove <player|all>"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops teleport <player|index> [n]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops fix"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops freeze <player|all|menu> [duration]"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops unfreeze <player|all|menu>"));
            sender.sendMessage(ItemUtils.colored("&e/itemshops vault <player>"));
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
                        int stock = dev.enthusia.itemshops.util.ItemUtils.countSimilar(cont.getInventory(), s.sell());
                        trades = stock / Math.max(1, s.sell().getAmount());
                    }
                    sender.sendMessage(Texts.fmt(ItemShopsPlugin.get().messages(), "search.line",
                            "world", s.sign().world, "x", s.sign().x, "y", s.sign().y, "z", s.sign().z,
                            "owner", owner,
                            "sell_amt", s.sell().getAmount(), "sell_name", dev.enthusia.itemshops.util.ItemUtils.niceName(s.sell()),
                            "cost_amt", s.cost().getAmount(), "cost_name", dev.enthusia.itemshops.util.ItemUtils.niceName(s.cost()),
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
                ItemShopsPlugin.get().shops().requestSave();
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
                ItemShopsPlugin.get().shops().requestSave();
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
            boolean all = args.length >= 2 && "all".equalsIgnoreCase(args[1]);
            if (all && !sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            new DeleteShopsMenu(ItemShopsPlugin.get(), mgr, p, all, 0).open();
            return true;
        }

        if ("breakdelete".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.breakdelete")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            String mode = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "5m";
            if ("off".equals(mode)) {
                ItemShopsPlugin.get().disableBreakDelete(p.getUniqueId());
                sender.sendMessage(ItemUtils.colored("&eBreak-delete mode disabled."));
                return true;
            }
            long durationMs = 5L * 60L * 1000L;
            if (!"on".equals(mode) && !"5m".equals(mode)) {
                try {
                    if (mode.endsWith("m")) {
                        long mins = Long.parseLong(mode.substring(0, mode.length() - 1));
                        durationMs = Math.max(1L, mins) * 60L * 1000L;
                    }
                } catch (NumberFormatException ignored) {}
            }
            ItemShopsPlugin.get().enableBreakDelete(p.getUniqueId(), durationMs);
            long mins = Math.max(1L, durationMs / 60000L);
            sender.sendMessage(ItemUtils.colored("&aBreak-delete mode enabled for " + mins + " minute(s)."));
            return true;
        }

        if ("adminview".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            boolean enable = args.length < 2 || "on".equalsIgnoreCase(args[1]);
            ItemShopsPlugin.get().setAdminView(p.getUniqueId(), enable);
            sender.sendMessage(ItemUtils.colored("&eAdmin view mode " + (enable ? "enabled" : "disabled") + "."));
            return true;
        }

        if ("info".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            boolean enable = args.length < 2 || "on".equalsIgnoreCase(args[1]);
            ItemShopsPlugin.get().setAdminInfo(p.getUniqueId(), enable);
            sender.sendMessage(ItemUtils.colored("&eAdmin info mode " + (enable ? "enabled" : "disabled") + "."));
            return true;
        }

        if ("breakothers".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            boolean enable = args.length < 2 || "on".equalsIgnoreCase(args[1]);
            ItemShopsPlugin.get().setBreakOthers(p.getUniqueId(), enable);
            sender.sendMessage(ItemUtils.colored("&eAdmin break-others mode " + (enable ? "enabled" : "disabled") + "."));
            return true;
        }

        if ("remove".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops remove <player|all>")); return true; }
            if ("all".equalsIgnoreCase(args[1])) {
                for (Shop s : new ArrayList<>(mgr.all())) mgr.deleteShop(s, ShopManager.RemovalReason.ADMIN_COMMAND, null);
                sender.sendMessage(ItemUtils.colored("&eRemoved all shops."));
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            for (Shop s : new ArrayList<>(mgr.ownedBy(op.getUniqueId()))) mgr.deleteShop(s, ShopManager.RemovalReason.ADMIN_COMMAND, null);
            sender.sendMessage(ItemUtils.colored("&eRemoved shops for &f" + op.getName() + "&e."));
            return true;
        }

        if ("teleport".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops teleport <player|index> [n]")); return true; }
            if ("index".equalsIgnoreCase(args[1])) {
                int idx = args.length >= 3 ? parseIndex(args[2]) : 1;
                var all = new ArrayList<>(mgr.all());
                all.sort(Comparator.comparing((Shop s) -> s.sign().world)
                        .thenComparingInt(s -> s.sign().x)
                        .thenComparingInt(s -> s.sign().y)
                        .thenComparingInt(s -> s.sign().z));
                if (idx < 1 || idx > all.size()) { sender.sendMessage(ItemUtils.colored("&cInvalid index.")); return true; }
                Shop s = all.get(idx - 1);
                var loc = s.sign().toLocation();
                if (loc == null) { sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.world-missing")); return true; }
                p.teleport(loc.add(0.5, 0, 0.5));
                sender.sendMessage(ItemUtils.colored("&eTeleported to shop #" + idx + "."));
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            int idx = args.length >= 3 ? parseIndex(args[2]) : 1;
            var list = mgr.ownedBy(op.getUniqueId());
            if (idx < 1 || idx > list.size()) { sender.sendMessage(ItemUtils.colored("&cInvalid index.")); return true; }
            Shop s = list.get(idx - 1);
            var loc = s.sign().toLocation();
            if (loc == null) { sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.world-missing")); return true; }
            p.teleport(loc.add(0.5, 0, 0.5));
            sender.sendMessage(ItemUtils.colored("&eTeleported to " + op.getName() + "'s shop #" + idx + "."));
            return true;
        }

        if ("fix".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            mgr.pruneInvalidShops();
            long nowMs = System.currentTimeMillis();
            boolean changed = false;
            for (Shop s : mgr.all()) {
                if (s.clearIfFreezeExpired(nowMs)) changed = true;
                ItemShopsPlugin.get().shops().updateSign(s);
            }
            if (changed) mgr.requestSave();
            sender.sendMessage(ItemUtils.colored("&aRefreshed shops and signs."));
            return true;
        }

        if ("freeze".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops freeze <player|all|menu> [duration]")); return true; }
            long durationMs = args.length >= 3 ? parseDurationMs(args[2], 0L) : 0L;
            if ("all".equalsIgnoreCase(args[1])) {
                for (Shop s : mgr.all()) {
                    if (durationMs > 0) s.freezeUntil(System.currentTimeMillis() + durationMs);
                    else s.freezeIndefinitely();
                }
                mgr.requestSave();
                sender.sendMessage(ItemUtils.colored("&eFroze all shops."));
                return true;
            }
            if ("menu".equalsIgnoreCase(args[1])) {
                if (args.length < 3) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops freeze menu <player> [duration]")); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
                if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
                long menuDurationMs = args.length >= 4 ? parseDurationMs(args[3], 0L) : 0L;
                new FreezeShopsMenu(ItemShopsPlugin.get(), mgr, p, op.getUniqueId(), menuDurationMs).open();
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            for (Shop s : mgr.ownedBy(op.getUniqueId())) {
                if (durationMs > 0) s.freezeUntil(System.currentTimeMillis() + durationMs);
                else s.freezeIndefinitely();
            }
            mgr.requestSave();
            sender.sendMessage(ItemUtils.colored("&eFroze shops for &f" + op.getName() + "&e."));
            return true;
        }

        if ("unfreeze".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops unfreeze <player|all|menu>")); return true; }
            if ("all".equalsIgnoreCase(args[1])) {
                for (Shop s : mgr.all()) s.unfreeze();
                mgr.requestSave();
                sender.sendMessage(ItemUtils.colored("&eUnfroze all shops."));
                return true;
            }
            if ("menu".equalsIgnoreCase(args[1])) {
                if (args.length < 3) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops unfreeze menu <player>")); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[2]);
                if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
                new FreezeShopsMenu(ItemShopsPlugin.get(), mgr, p, op.getUniqueId(), 0L).open();
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            for (Shop s : mgr.ownedBy(op.getUniqueId())) s.unfreeze();
            mgr.requestSave();
            sender.sendMessage(ItemUtils.colored("&eUnfroze shops for &f" + op.getName() + "&e."));
            return true;
        }

        if ("vault".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!sender.hasPermission("itemshops.admin")) {
                sender.sendMessage(Texts.msg(ItemShopsPlugin.get().messages(), "errors.no-permission"));
                return true;
            }
            if (args.length < 2) { sender.sendMessage(ItemUtils.colored("&eUsage: /itemshops vault <player>")); return true; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || (op.getName()==null && !op.hasPlayedBefore())) { sender.sendMessage(ItemUtils.colored("&cUnknown player.")); return true; }
            new VaultAdminMenu(ItemShopsPlugin.get(), p, op.getUniqueId()).open();
            return true;
        }

        sender.sendMessage(ItemUtils.colored("&cUnknown subcommand."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("reload","search","trust","untrust","list","edit","delete","breakdelete","adminview","info","breakothers","remove","teleport","fix","freeze","unfreeze","vault");

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
        if ("delete".equalsIgnoreCase(args[0]) && args.length == 2 && sender.hasPermission("itemshops.admin")) return Arrays.asList("all");
        if ("breakdelete".equalsIgnoreCase(args[0]) && args.length == 2) return Arrays.asList("on","off","5m","10m","30m","60m");
        if ("adminview".equalsIgnoreCase(args[0]) && args.length == 2) return Arrays.asList("on","off");
        if ("info".equalsIgnoreCase(args[0]) && args.length == 2) return Arrays.asList("on","off");
        if ("breakothers".equalsIgnoreCase(args[0]) && args.length == 2) return Arrays.asList("on","off");
        if ("remove".equalsIgnoreCase(args[0]) && args.length == 2) {
            List<String> out = new ArrayList<>();
            if (sender.hasPermission("itemshops.admin")) out.add("all");
            String pfx = args[1].toLowerCase(Locale.ROOT);
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList()));
            return out;
        }
        if ("teleport".equalsIgnoreCase(args[0]) && args.length == 2) {
            String pfx = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            if ("index".startsWith(pfx)) out.add("index");
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList()));
            return out;
        }
        if ("teleport".equalsIgnoreCase(args[0]) && args.length == 3) {
            if ("index".equalsIgnoreCase(args[1])) {
                int max = Math.min(10, mgr.all().size());
                List<String> nums = new ArrayList<>();
                for (int i=1;i<=max;i++) nums.add(String.valueOf(i));
                return nums;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op != null && (op.hasPlayedBefore() || op.getName() != null)) {
                int max = Math.min(10, mgr.ownedBy(op.getUniqueId()).size());
                List<String> nums = new ArrayList<>();
                for (int i=1;i<=max;i++) nums.add(String.valueOf(i));
                return nums;
            }
        }
        if ("freeze".equalsIgnoreCase(args[0]) && args.length == 2) {
            List<String> base = new ArrayList<>(Arrays.asList("all","menu"));
            String pfx = args[1].toLowerCase(Locale.ROOT);
            base.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList()));
            return base;
        }
        if ("unfreeze".equalsIgnoreCase(args[0]) && args.length == 2) {
            List<String> base = new ArrayList<>(Arrays.asList("all","menu"));
            String pfx = args[1].toLowerCase(Locale.ROOT);
            base.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList()));
            return base;
        }
        if ("freeze".equalsIgnoreCase(args[0]) && args.length == 3 && "menu".equalsIgnoreCase(args[1])) {
            String pfx = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
        }
        if ("unfreeze".equalsIgnoreCase(args[0]) && args.length == 3 && "menu".equalsIgnoreCase(args[1])) {
            String pfx = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
        }
        if (("vault".equalsIgnoreCase(args[0])) && args.length == 2) {
            String pfx = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(pfx))
                    .sorted()
                    .limit(50)
                    .collect(Collectors.toList());
        }
        if ("freeze".equalsIgnoreCase(args[0]) && args.length == 3 && !"menu".equalsIgnoreCase(args[1]) && !"all".equalsIgnoreCase(args[1])) {
            return Arrays.asList("5m","10m","30m","60m","120m");
        }
        if ("freeze".equalsIgnoreCase(args[0]) && args.length == 4 && "menu".equalsIgnoreCase(args[1])) {
            return Arrays.asList("5m","10m","30m","60m","120m");
        }
        return List.of();
    }

    private int parseIndex(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private long parseDurationMs(String raw, long fallbackMs) {
        if (raw == null || raw.isEmpty()) return fallbackMs;
        String r = raw.toLowerCase(Locale.ROOT);
        try {
            if (r.endsWith("m")) {
                long mins = Long.parseLong(r.substring(0, r.length()-1));
                return Math.max(1, mins) * 60L * 1000L;
            }
            if (r.endsWith("h")) {
                long hrs = Long.parseLong(r.substring(0, r.length()-1));
                return Math.max(1, hrs) * 60L * 60L * 1000L;
            }
            long mins = Long.parseLong(r);
            return Math.max(1, mins) * 60L * 1000L;
        } catch (NumberFormatException ex) {
            return fallbackMs;
        }
    }
}
