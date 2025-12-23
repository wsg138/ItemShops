package dev.enthusia.itemshops.command;

import dev.enthusia.itemshops.ItemShopsPlugin;
import dev.enthusia.itemshops.region.MarketRegionManager;
import dev.enthusia.itemshops.util.ItemUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MarketCommand implements CommandExecutor, TabCompleter {
    private final ItemShopsPlugin plugin;

    public MarketCommand(ItemShopsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("itemshops.market")) {
            sender.sendMessage(ItemUtils.colored("&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ItemUtils.colored("&eUsage: /shopmarket set <radius> | add <world> <minX> <minY> <minZ> <maxX> <maxY> <maxZ> | clear | list"));
            return true;
        }

        MarketRegionManager mr = plugin.market();
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 2) { p.sendMessage(ItemUtils.colored("&eUsage: /shopmarket set <radius>")); return true; }
                int radius;
                try { radius = Integer.parseInt(args[1]); if (radius < 1) throw new NumberFormatException(); }
                catch (NumberFormatException ex) { p.sendMessage(ItemUtils.colored("&cRadius must be a positive number.")); return true; }
                mr.addCenteredCube(p.getLocation(), radius);
                p.sendMessage(ItemUtils.colored("&aAdded market cube here with radius &f" + radius));
                return true;
            }
            case "add" -> {
                if (args.length < 8) {
                    sender.sendMessage(ItemUtils.colored("&eUsage: /shopmarket add <world> <minX> <minY> <minZ> <maxX> <maxY> <maxZ>"));
                    return true;
                }
                String worldName = args[1];
                if (Bukkit.getWorld(worldName) == null) {
                    sender.sendMessage(ItemUtils.colored("&cUnknown world: &f" + worldName));
                    return true;
                }
                int minX, minY, minZ, maxX, maxY, maxZ;
                try {
                    minX = Integer.parseInt(args[2]);
                    minY = Integer.parseInt(args[3]);
                    minZ = Integer.parseInt(args[4]);
                    maxX = Integer.parseInt(args[5]);
                    maxY = Integer.parseInt(args[6]);
                    maxZ = Integer.parseInt(args[7]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage(ItemUtils.colored("&cAll coordinates must be integers."));
                    return true;
                }
                mr.addRegion(worldName, minX, minY, minZ, maxX, maxY, maxZ);
                sender.sendMessage(ItemUtils.colored("&aAdded market cuboid in &f" + worldName));
                return true;
            }
            case "clear" -> {
                mr.clear();
                sender.sendMessage(ItemUtils.colored("&aCleared all market regions."));
                return true;
            }
            case "list" -> {
                var regs = mr.regions();
                sender.sendMessage(ItemUtils.colored("&6Market regions: &f" + regs.size()));
                int i = 1;
                for (var r : regs) {
                    sender.sendMessage(ItemUtils.colored("&7" + (i++) + ") &f" + r.world +
                            " &7[&f" + r.minX + "," + r.minY + "," + r.minZ + "&7] -> [&f" +
                            r.maxX + "," + r.maxY + "," + r.maxZ + "&7]"));
                }
                return true;
            }
            default -> {
                sender.sendMessage(ItemUtils.colored("&eUsage: /shopmarket set <radius> | add <world> <minX> <minY> <minZ> <maxX> <maxY> <maxZ> | clear | list"));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("set","add","clear","list");
        if (args.length == 2 && "set".equalsIgnoreCase(args[0])) return List.of("16","32","48","64");
        if (args.length == 2 && "add".equalsIgnoreCase(args[0])) return new ArrayList<>(Bukkit.getWorlds().stream().map(w -> w.getName()).toList());
        return Collections.emptyList();
    }
}
