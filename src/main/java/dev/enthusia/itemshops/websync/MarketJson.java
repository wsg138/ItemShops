package dev.enthusia.itemshops.websync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.nio.charset.StandardCharsets;

public final class MarketJson {
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .disableHtmlEscaping()
            .registerTypeAdapter(MarketDtos.Avatar.class, (JsonSerializer<MarketDtos.Avatar>) MarketJson::avatar)
            .registerTypeAdapter(MarketDtos.Container.class, (JsonSerializer<MarketDtos.Container>) MarketJson::container)
            .registerTypeAdapter(MarketDtos.ContainerEntry.class, (JsonSerializer<MarketDtos.ContainerEntry>) MarketJson::containerEntry)
            .create();

    private MarketJson() {}
    public static String encode(Object value) { return GSON.toJson(value); }
    public static byte[] encodeBytes(Object value) { return encode(value).getBytes(StandardCharsets.UTF_8); }

    private static JsonObject avatar(MarketDtos.Avatar value, java.lang.reflect.Type type, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", value.kind());
        if (value.source() != null) json.addProperty("source", value.source());
        if (value.includesOuterLayer() != null) json.addProperty("includesOuterLayer", value.includesOuterLayer());
        if (value.url() != null) json.addProperty("url", value.url());
        return json;
    }

    private static JsonObject container(MarketDtos.Container value, java.lang.reflect.Type type, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("type", value.type());
        if (value.slots() != null) json.addProperty("slots", value.slots());
        if (value.capacityUsed() != null) json.addProperty("capacityUsed", value.capacityUsed());
        if (value.capacityMax() != null) json.addProperty("capacityMax", value.capacityMax());
        json.add("contents", context.serialize(value.contents()));
        return json;
    }

    private static JsonObject containerEntry(MarketDtos.ContainerEntry value, java.lang.reflect.Type type, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        if (value.slot() != null) json.addProperty("slot", value.slot());
        json.add("item", context.serialize(value.item()));
        return json;
    }
}
