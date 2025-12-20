package com.lincoln.itemshops.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;


public final class Bedrock {
    private Bedrock() {}

    
    private static final boolean FLOODGATE_PRESENT;
    private static final Object FLOODGATE_API_INSTANCE;
    private static final Method IS_FLOODGATE_PLAYER;

    static {
        Object apiInstance = null;
        Method isFloodgatePlayer = null;
        boolean present = false;

        try {
            
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            apiInstance = getInstance.invoke(null);

            
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            present = (apiInstance != null && isFloodgatePlayer != null);
        } catch (Throwable ignored) {
            present = false;
            apiInstance = null;
            isFloodgatePlayer = null;
        }

        FLOODGATE_PRESENT = present;
        FLOODGATE_API_INSTANCE = apiInstance;
        IS_FLOODGATE_PLAYER = isFloodgatePlayer;
    }

    public static boolean isBedrock(UUID uuid) {
        if (!FLOODGATE_PRESENT || uuid == null) return false;
        try {
            return (boolean) IS_FLOODGATE_PLAYER.invoke(FLOODGATE_API_INSTANCE, uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isBedrock(Player p) {
        return p != null && isBedrock(p.getUniqueId());
    }
}
