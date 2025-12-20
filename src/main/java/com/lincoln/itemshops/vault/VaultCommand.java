package com.lincoln.itemshops.command;

import com.lincoln.itemshops.ItemShopsPlugin;
import com.lincoln.itemshops.gui.VaultMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public final class VaultCommand implements CommandExecutor, TabCompleter {
    private final ItemShopsPlugin plugin;
    public VaultCommand(ItemShopsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("[ItemShops] Players only."); return true; }
        new VaultMenu(plugin, p).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
