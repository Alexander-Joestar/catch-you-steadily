package dev.catchyou.steadily.internal;

public final class FallTrajectory {
    private static final double GRAVITY = 0.08;
    private static final double HORIZONTAL_DRAG = 0.91;
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
            velocityX = movement.x == velocityX ? velocityX * HORIZONTAL_DRAG : 0;
            velocityY = (velocityY - GRAVITY) * VERTICAL_DRAG;
            velocityZ = movement.z == velocityZ ? velocityZ * HORIZONTAL_DRAG : 0;
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
        double halfWidth = width / 2;
        if (gap < 0 || gap > 2.0 || gap > -velocityY * 2
                || x + halfWidth <= minX || x - halfWidth >= maxX
                || z + halfWidth <= minZ || z - halfWidth >= maxZ) {
            return false;
        }
        double ticksToContact = gap / -velocityY;
        double projectedX = x + velocityX * ticksToContact;
        double projectedZ = z + velocityZ * ticksToContact;
        return projectedX + halfWidth > minX && projectedX - halfWidth < maxX
                && projectedZ + halfWidth > minZ && projectedZ - halfWidth < maxZ;
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
