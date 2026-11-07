package dev.catchyou.steadily.legacy1122;

import dev.catchyou.steadily.internal.RescueConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

@Config(modid = "catch_you_steadily", name = "catch-you-steadily")
public final class LegacyConfig {
    @Config.Name("keep-riding")
    public static boolean keepRiding = false;
    @Config.Name("exclude-hostile")
    public static boolean excludeHostile = false;
    @Config.Name("excluded-entities")
    public static String[] excludedEntities = new String[0];
    @Config.Name("attraction-radius")
    public static double attractionRadius = 24;
    @Config.Name("vertical-range")
    public static double verticalRange = 12;

    static void validate(File file) throws IOException {
        if (!file.exists()) return;
        byte[] original = Files.readAllBytes(file.toPath());
        Configuration config = new Configuration(file);
        if (!file.exists() || !Arrays.equals(original, Files.readAllBytes(file.toPath()))) {
            throw new IllegalArgumentException("Forge could not parse " + file);
        }
        RescueConfig.of(bool(property(config, "exclude-hostile"), "exclude-hostile"),
                ids(property(config, "excluded-entities")),
                number(property(config, "attraction-radius"), "attraction-radius", 24),
                number(property(config, "vertical-range"), "vertical-range", 12),
                bool(property(config, "keep-riding"), "keep-riding"));
    }

    static RescueConfig snapshot() {
        return RescueConfig.of(excludeHostile, new HashSet<>(Arrays.asList(excludedEntities)),
                attractionRadius, verticalRange, keepRiding);
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
