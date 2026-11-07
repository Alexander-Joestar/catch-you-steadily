package dev.catchyou.steadily.legacy;

import dev.catchyou.steadily.internal.RescueConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

final class LegacyConfig {
    static RescueConfig load(File file) throws IOException {
        byte[] original = file.exists() ? Files.readAllBytes(file.toPath()) : null;
        Configuration config = new Configuration(file);
        if (original != null && (!file.exists() || !Arrays.equals(original, Files.readAllBytes(file.toPath())))) {
            throw new IllegalArgumentException("Forge could not parse " + file);
        }
        Property keep = property(config, "keep-riding");
        Property hostile = property(config, "exclude-hostile");
        Property excluded = property(config, "excluded-entities");
        Property radius = property(config, "attraction-radius");
        Property vertical = property(config, "vertical-range");
        RescueConfig snapshot = RescueConfig.of(bool(hostile, "exclude-hostile"), ids(excluded),
                number(radius, "attraction-radius", 24), number(vertical, "vertical-range", 12),
                bool(keep, "keep-riding"));
        config.get("general", "keep-riding", false);
        config.get("general", "exclude-hostile", false);
        config.get("general", "excluded-entities", new String[0]);
        config.get("general", "attraction-radius", 24.0);
        config.get("general", "vertical-range", 12.0);
        config.save();
        return snapshot;
    }

    private static Property property(Configuration config, String name) {
        return config.hasCategory("general") ? config.getCategory("general").get(name) : null;
    }

    private static boolean bool(Property property, String key) {
        if (property == null) return false;
        String value = property.getString();
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    private static double number(Property property, String key, double defaultValue) {
        if (property == null) return defaultValue;
        try {
            return Double.parseDouble(property.getString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be a number", exception);
        }
    }

    private static HashSet<String> ids(Property property) {
        if (property == null) return new HashSet<>();
        if (!property.isList()) throw new IllegalArgumentException("excluded-entities must be a list");
        return new HashSet<>(Arrays.asList(property.getStringList()));
    }
}
