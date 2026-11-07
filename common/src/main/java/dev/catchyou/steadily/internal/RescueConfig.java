package dev.catchyou.steadily.internal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RescueConfig {
    private final boolean keepRiding;
    private final boolean excludeHostile;
    private final Set<String> excludedEntities;
    private final double attractionRadius;
    private final double verticalRange;

    private RescueConfig(boolean excludeHostile, Set<String> excludedEntities,
                         double attractionRadius, double verticalRange, boolean keepRiding) {
        this.keepRiding = keepRiding;
        this.excludeHostile = excludeHostile;
        this.excludedEntities = Collections.unmodifiableSet(new HashSet<>(excludedEntities));
        this.attractionRadius = attractionRadius;
        this.verticalRange = verticalRange;
    }

    public static RescueConfig of(boolean excludeHostile, Set<String> excludedEntities,
                                  double attractionRadius, double verticalRange, boolean keepRiding) {
        for (String id : excludedEntities) {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException("Invalid entity ID: " + id);
            }
        }
        validateRange("attraction-radius", attractionRadius, 1, 32);
        validateRange("vertical-range", verticalRange, 1, 16);
        return new RescueConfig(excludeHostile, excludedEntities, attractionRadius, verticalRange, keepRiding);
    }

    public boolean keepRiding() {
        return keepRiding;
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

    private static void validateRange(String key, double value, double minimum, double maximum) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " must be between " + minimum + " and " + maximum);
        }
    }
}
