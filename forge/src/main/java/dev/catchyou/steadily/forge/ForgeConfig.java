package dev.catchyou.steadily.forge;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.toml.TomlFormat;
import dev.catchyou.steadily.internal.RescueConfig;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

final class ForgeConfig {
    static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue EXCLUDE_HOSTILE;
    private static final ForgeConfigSpec.BooleanValue KEEP_RIDING;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_ENTITIES;
    private static final ForgeConfigSpec.DoubleValue ATTRACTION_RADIUS;
    private static final ForgeConfigSpec.DoubleValue VERTICAL_RANGE;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        KEEP_RIDING = builder.define("keep-riding", false);
        EXCLUDE_HOSTILE = builder.define("exclude-hostile", false);
        EXCLUDED_ENTITIES = builder.defineListAllowEmpty("excluded-entities", Collections.emptyList(), item -> item instanceof String);
        ATTRACTION_RADIUS = builder.defineInRange("attraction-radius", 24.0, 1.0, 32.0);
        VERTICAL_RANGE = builder.defineInRange("vertical-range", 12.0, 1.0, 16.0);
        SPEC = builder.build();
    }

    static void validate(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Config config;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            config = TomlFormat.instance().createParser().parse(reader);
        }
        Object excluded = config.get("excluded-entities");
        HashSet<String> ids = new HashSet<>();
        if (excluded != null) {
            if (!(excluded instanceof List<?>)) throw new IllegalArgumentException("excluded-entities must be an array");
            for (Object id : (List<?>)excluded) {
                if (!(id instanceof String)) throw new IllegalArgumentException("excluded-entities must contain strings");
                ids.add((String)id);
            }
        }
        RescueConfig.of(bool(config, "exclude-hostile"), ids, number(config, "attraction-radius", 24),
                number(config, "vertical-range", 12), bool(config, "keep-riding"));
    }

    private static boolean bool(Config config, String key) {
        Object value = config.get(key);
        if (value == null) return false;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " must be a boolean");
        return (Boolean)value;
    }

    private static double number(Config config, String key, double defaultValue) {
        Object value = config.get(key);
        if (value == null) return defaultValue;
        if (!(value instanceof Number)) throw new IllegalArgumentException(key + " must be a number");
        return ((Number)value).doubleValue();
    }

    static RescueConfig snapshot() {
        if (!SPEC.isLoaded()) throw new IllegalStateException("Configuration has not loaded");
        return RescueConfig.of(EXCLUDE_HOSTILE.get(), new HashSet<>(EXCLUDED_ENTITIES.get()),
                ATTRACTION_RADIUS.get(), VERTICAL_RANGE.get(), KEEP_RIDING.get());
    }
}
