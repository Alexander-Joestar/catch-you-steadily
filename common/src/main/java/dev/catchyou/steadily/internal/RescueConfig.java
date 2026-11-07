package dev.catchyou.steadily.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public final class RescueConfig {
    private static final String EXCLUDE_HOSTILE = "exclude-hostile";
    private static final String EXCLUDED_ENTITIES = "excluded-entities";
    private static final String ATTRACTION_RADIUS = "attraction-radius";
    private static final String VERTICAL_RANGE = "vertical-range";
    private static final String KEEP_RIDING = "keep-riding";
    private final boolean keepRiding;
    private final boolean excludeHostile;
    private final Set<String> excludedEntities;
    private final double attractionRadius;
    private final double verticalRange;

    private RescueConfig(boolean excludeHostile, Set<String> excludedEntities,
                         double attractionRadius, double verticalRange, boolean keepRiding) {
        this.keepRiding = keepRiding;
        this.excludeHostile = excludeHostile;
        this.excludedEntities = Collections.unmodifiableSet(excludedEntities);
        this.attractionRadius = attractionRadius;
        this.verticalRange = verticalRange;
    }

    public static RescueConfig load(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.notExists(path)) {
            Files.write(path, ("# Exclude all hostile mobs.\n"
                    + EXCLUDE_HOSTILE + "=false\n"
                    + "# Comma-separated entity IDs, for example minecraft:creeper.\n"
                    + EXCLUDED_ENTITIES + "=\n"
                    + "# Horizontal search radius in blocks (1-32).\n"
                    + ATTRACTION_RADIUS + "=24\n"
                    + "# Vertical search range in blocks (1-16).\n"
                    + VERTICAL_RANGE + "=12\n"
                    + "# Read at server startup. False dismounts on safe landing; true keeps riding.\n"
                    + KEEP_RIDING + "=false\n").getBytes(StandardCharsets.UTF_8));
        }
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        boolean excludeHostile = parseBoolean(properties, EXCLUDE_HOSTILE);
        boolean keepRiding = parseBoolean(properties, KEEP_RIDING);
        Set<String> excludedEntities = new HashSet<>();
        for (String id : properties.getProperty(EXCLUDED_ENTITIES, "").split(",")) {
            String normalized = id.trim();
            if (!normalized.isEmpty()) {
                excludedEntities.add(normalized);
            }
        }
        double attractionRadius = parseRange(properties, ATTRACTION_RADIUS, 24, 1, 32);
        double verticalRange = parseRange(properties, VERTICAL_RANGE, 12, 1, 16);
        return new RescueConfig(excludeHostile, excludedEntities, attractionRadius, verticalRange, keepRiding);
    }

    public boolean keepRiding() {
        return keepRiding;
    }

    private static boolean parseBoolean(Properties properties, String key) {
        String value = properties.getProperty(key, "false").trim();
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public boolean excludes(String entityId, boolean hostile) {
        return (excludeHostile && hostile) || (entityId != null
                && excludedEntities.stream().anyMatch(configuredId -> configuredId.equalsIgnoreCase(entityId)));
    }

    public double attractionRadius() {
        return attractionRadius;
    }

    public double verticalRange() {
        return verticalRange;
    }

    private static double parseRange(Properties properties, String key, double defaultValue,
                                     double minimum, double maximum) {
        final double value;
        try {
            value = Double.parseDouble(properties.getProperty(key, Double.toString(defaultValue)).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid numeric value for " + key, exception);
        }
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
