package com.alpha.lanim.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public final class JsonUtil {

    private static final Gson GSON = new GsonBuilder().create();

    private JsonUtil() {}

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object obj) {
        return GSON.toJson(obj);
    }

    public static byte[] toJsonBytes(Object obj) {
        return toJson(obj).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    public static <T> T fromJson(byte[] jsonBytes, Class<T> clazz) {
        return fromJson(new String(jsonBytes, java.nio.charset.StandardCharsets.UTF_8), clazz);
    }

    public static <T> T fromJson(JsonObject obj, Class<T> clazz) {
        return GSON.fromJson(obj, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromPayload(Object payload, Class<T> clazz) {
        if (payload == null) return null;
        if (clazz.isInstance(payload)) {
            return (T) payload;
        }
        if (payload instanceof JsonObject) {
            return GSON.fromJson((JsonObject) payload, clazz);
        }
        return GSON.fromJson(GSON.toJson(payload), clazz);
    }
}
