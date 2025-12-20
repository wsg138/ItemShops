package com.lincoln.itemshops.util;

import org.bukkit.configuration.file.FileConfiguration;

public final class Texts {
    private Texts(){}
    public static String msg(FileConfiguration messages, String path) {
        return ItemUtils.colored(messages.getString(path, path));
    }
    public static String fmt(FileConfiguration messages, String path, Object... kv) {
        String raw = messages.getString(path, path);
        String out = raw;
        for (int i=0;i+1<kv.length;i+=2) {
            String k = String.valueOf(kv[i]);
            String v = String.valueOf(kv[i+1]);
            out = out.replace("{"+k+"}", v);
        }
        return ItemUtils.colored(out);
    }
}
