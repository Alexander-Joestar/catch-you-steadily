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
                               double minX, double maxX, double minZ, double maxZ, double bottomY, double topY,
                               double mobVelocityX, double mobVelocityY, double mobVelocityZ,
                               CollisionProbe collisionProbe, CollisionProbe mobCollisionProbe) {
        double gap = y - topY;
        if (velocityY >= 0 || gap < 0 || gap > -velocityY + Math.abs(mobVelocityY)) {
            return false;
        }
        Movement mobMovement = mobCollisionProbe.move((minX + maxX) / 2.0, bottomY, (minZ + maxZ) / 2.0,
                mobVelocityX, mobVelocityY, mobVelocityZ);
        double relativeY = velocityY - mobMovement.y;
        if (mobMovement.liquid || relativeY >= 0 || gap > -relativeY) {
            return false;
        }
        double fraction = gap / -relativeY;
        double dx = velocityX * fraction;
        double dy = velocityY * fraction;
        double dz = velocityZ * fraction;
        double contactX = x + dx - mobMovement.x * fraction;
        double contactZ = z + dz - mobMovement.z * fraction;
        double halfWidth = width / 2.0;
        if (contactX + halfWidth <= minX || contactX - halfWidth >= maxX
                || contactZ + halfWidth <= minZ || contactZ - halfWidth >= maxZ) {
            return false;
        }
        Movement movement = collisionProbe.move(x, y, z, dx, dy, dz);
        return !movement.liquid && Math.abs(movement.x - dx) < 1.0E-7
                && Math.abs(movement.y - dy) < 1.0E-7 && Math.abs(movement.z - dz) < 1.0E-7;
    }

    public boolean touchesSeat(double x, double y, double z, double width, double height,
                               double minX, double maxX, double minZ, double maxZ, double topY,
                               CollisionProbe collisionProbe) {
        double halfWidth = width / 2.0;
        if (y > topY || y + height <= topY || x + halfWidth <= minX || x - halfWidth >= maxX
                || z + halfWidth <= minZ || z - halfWidth >= maxZ) {
            return false;
        }
        double dy = topY - y;
        Movement movement = collisionProbe.move(x, y, z, 0, dy, 0);
        return !movement.liquid && Math.abs(movement.y - dy) < 1.0E-7;
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
