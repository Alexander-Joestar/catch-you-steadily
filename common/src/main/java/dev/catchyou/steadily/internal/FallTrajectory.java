package dev.catchyou.steadily.internal;

public final class FallTrajectory {
    private static final double GRAVITY = 0.08;
    private static final double HORIZONTAL_RETENTION = 0.98;
    private static final double VERTICAL_DRAG = 0.98;
    private static final int MAX_TICKS = 100;
    private static final double DAMAGE_DISTANCE = 3.0;

    public Result predict(double x, double y, double z, double velocityX, double velocityY,
                          double velocityZ, double fallDistance, CollisionProbe collisionProbe) {
        if (velocityY >= 0) {
            return Result.none();
        }
        double startY = y;
        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            Movement movement = collisionProbe.move(x, y, z, velocityX, velocityY, velocityZ);
            if (movement.liquid) {
                return Result.none();
            }
            x += movement.x;
            y += movement.y;
            z += movement.z;
            if (movement.y > velocityY) {
                return fallDistance + startY - y > DAMAGE_DISTANCE
                        ? new Result(x, y, z, tick) : Result.none();
            }
            velocityX = movement.x == velocityX ? velocityX * HORIZONTAL_RETENTION : 0;
            velocityY = (velocityY - GRAVITY) * VERTICAL_DRAG;
            velocityZ = movement.z == velocityZ ? velocityZ * HORIZONTAL_RETENTION : 0;
        }
        return Result.none();
    }

    public boolean reachesSeat(double x, double y, double z, double width,
                               double velocityX, double velocityY, double velocityZ,
                               double minX, double maxX, double minZ, double maxZ, double topY) {
        if (velocityY >= 0) {
            return false;
        }
        double gap = y - topY;
        double halfWidth = width / 2.0;
        double maxFallSpan = Math.max(2.5, -velocityY * 1.5);
        if (gap < -0.2 || gap > maxFallSpan) {
            return false;
        }
        double contactFraction = gap <= 0 ? 0.0 : Math.min(1.0, gap / -velocityY);
        double contactX = x + velocityX * contactFraction;
        double contactZ = z + velocityZ * contactFraction;
        double margin = 0.3;
        boolean overlapX = (contactX + halfWidth > minX - margin) && (contactX - halfWidth < maxX + margin);
        boolean overlapZ = (contactZ + halfWidth > minZ - margin) && (contactZ - halfWidth < maxZ + margin);
        return overlapX && overlapZ;
    }

    public interface CollisionProbe {
        Movement move(double x, double y, double z, double dx, double dy, double dz);
    }

    public static final class Movement {
        public final double x;
        public final double y;
        public final double z;
        public final boolean liquid;

        public Movement(double x, double y, double z, boolean liquid) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.liquid = liquid;
        }
    }

    public static final class Result {
        private static final Result NONE = new Result(Double.NaN, Double.NaN, Double.NaN, -1);
        public final double x;
        public final double y;
        public final double z;
        public final int ticks;

        private Result(double x, double y, double z, int ticks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticks = ticks;
        }

        public static Result none() {
            return NONE;
        }

        public boolean exists() {
            return ticks > 0;
        }
    }
}
