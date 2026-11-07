package dev.catchyou.steadily.internal;

public final class RescueFormation {
    private RescueFormation() {
    }

    public static TargetPoint computeTarget(double targetX, double targetZ,
                                            int followerIndex, double spacing) {
        if (followerIndex == 0) {
            return new TargetPoint(targetX, targetZ);
        }
        int ring = (int)Math.ceil((Math.sqrt(followerIndex + 1.0) - 1.0) / 2.0);
        int offset = followerIndex - (2 * ring - 1) * (2 * ring - 1);
        int side = offset / (2 * ring);
        int position = offset % (2 * ring);
        int x;
        int z;
        switch (side) {
            case 0:
                x = ring;
                z = -ring + position;
                break;
            case 1:
                x = ring - position;
                z = ring;
                break;
            case 2:
                x = -ring;
                z = ring - position;
                break;
            default:
                x = -ring + position;
                z = -ring;
                break;
        }
        return new TargetPoint(targetX + x * spacing, targetZ + z * spacing);
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
