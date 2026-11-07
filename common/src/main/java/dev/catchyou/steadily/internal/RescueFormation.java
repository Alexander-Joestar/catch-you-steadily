package dev.catchyou.steadily.internal;

public final class RescueFormation {
    private static final double DEFAULT_PERIMETER_RADIUS = 0.9;

    private RescueFormation() {
    }

    public static TargetPoint computeTarget(double targetX, double targetZ,
                                            int followerIndex, int totalFollowers) {
        if (totalFollowers <= 1 || followerIndex <= 0) {
            return new TargetPoint(targetX, targetZ);
        }
        int perimeterCount = totalFollowers - 1;
        double angle = (2.0 * Math.PI * (followerIndex - 1)) / perimeterCount;
        double offsetX = DEFAULT_PERIMETER_RADIUS * Math.cos(angle);
        double offsetZ = DEFAULT_PERIMETER_RADIUS * Math.sin(angle);
        return new TargetPoint(targetX + offsetX, targetZ + offsetZ);
    }

    public static final class TargetPoint {
        public final double x;
        public final double z;

        public TargetPoint(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
