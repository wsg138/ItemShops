package dev.enthusia.itemshops.command;

import dev.enthusia.itemshops.util.ItemUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class StoreCommand implements CommandExecutor {
    private static final String STORE_URL = "https://enthusia-shop.tebex.io/";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TextComponent prefix = new TextComponent(ItemUtils.colored("&6&lStore &8» &7"));
        TextComponent link = new TextComponent(ItemUtils.colored("&aClick here to open the shop"));
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, STORE_URL));
        link.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Open ").append(STORE_URL).color(net.md_5.bungee.api.ChatColor.YELLOW).create()
        ));
        prefix.addExtra(link);
        sender.spigot().sendMessage(prefix);
        return true;
    }
}
