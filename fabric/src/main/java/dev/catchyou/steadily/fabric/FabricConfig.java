package dev.catchyou.steadily.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.catchyou.steadily.internal.RescueConfig;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

final class FabricConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static RescueConfig load(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            JsonObject defaults = new JsonObject();
            defaults.addProperty("keep-riding", false);
            defaults.addProperty("exclude-hostile", false);
            defaults.add("excluded-entities", new JsonArray());
            defaults.addProperty("attraction-radius", 24);
            defaults.addProperty("vertical-range", 12);
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(defaults, writer);
            }
        }
        JsonElement root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(reader);
        }
        if (root == null || !root.isJsonObject()) {
            throw new IllegalArgumentException("Configuration must be a JSON object");
        }
        JsonObject values = root.getAsJsonObject();
        Set<String> excluded = new HashSet<>();
        JsonElement list = values.get("excluded-entities");
        if (list != null) {
            if (!list.isJsonArray()) {
                throw new IllegalArgumentException("excluded-entities must be an array of strings");
            }
            for (JsonElement element : list.getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException("excluded-entities must be an array of strings");
                }
                excluded.add(element.getAsString());
            }
        }
        return RescueConfig.of(bool(values, "exclude-hostile"), excluded,
                number(values, "attraction-radius", 24), number(values, "vertical-range", 12),
                bool(values, "keep-riding"));
    }

    private static boolean bool(JsonObject values, String key) {
        JsonElement value = values.get(key);
        if (value == null) return false;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(key + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static double number(JsonObject values, String key, double defaultValue) {
        JsonElement value = values.get(key);
        if (value == null) return defaultValue;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return value.getAsDouble();
    }
}
