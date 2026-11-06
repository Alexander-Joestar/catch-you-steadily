package dev.catchyou.steadily.internal;

import dev.catchyou.steadily.api.FallPrediction;

public final class FallPredictionImpl implements FallPrediction {
    @Override
    public Result predict(double x, double y, double z, double velocityX, double velocityY,
                          double velocityZ, double targetY, double gravity, double drag, int maxTicks) {
        requireFinite(x, y, z, velocityX, velocityY, velocityZ, targetY, gravity, drag);
        if (gravity < 0 || drag <= 0 || drag > 1 || maxTicks < 0) {
            throw new IllegalArgumentException("Invalid motion parameters");
        }
        if (y <= targetY) {
            return new Result(x, y, z, 0);
        }
        for (int tick = 1; tick <= maxTicks; tick++) {
            x += velocityX;
            y += velocityY;
            z += velocityZ;
            if (y <= targetY) {
                return new Result(x, y, z, tick);
            }
            velocityX *= drag;
            velocityY = (velocityY - gravity) * drag;
            velocityZ *= drag;
        }
        return new Result(x, y, z, -1);
    }

    @Override
    public boolean canReach(Result result, double x, double z, double speedPerTick, double tolerance) {
        requireFinite(x, z, speedPerTick, tolerance);
        if (speedPerTick < 0 || tolerance < 0) {
            throw new IllegalArgumentException("Speed and tolerance must be nonnegative");
        }
        return result.ticks >= 0 && Math.hypot(result.x - x, result.z - z)
                <= speedPerTick * result.ticks + tolerance;
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Motion parameters must be finite");
            }
        }
    }
}
