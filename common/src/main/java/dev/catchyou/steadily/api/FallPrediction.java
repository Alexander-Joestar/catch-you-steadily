package dev.catchyou.steadily.api;

import dev.catchyou.steadily.internal.FallPredictionImpl;

/**
 * 根据离散运动参数预测玩家到达指定高度的时间和水平位置。
 * 实例无状态，可在任意线程调用，不读取或修改游戏世界。
 */
public interface FallPrediction {
    /** 创建无状态预测器。 */
    static FallPrediction create() {
        return new FallPredictionImpl();
    }

    /**
     * 按每 tick 位置先变化、速度再受重力和阻力影响的顺序预测。
     * 高度已到达目标或在 maxTicks 内未到达时返回的 tick 数分别为零或负一。
     * 参数必须为有限值，drag 在 (0, 1] 内，gravity 非负，maxTicks 非负。
     */
    Result predict(double x, double y, double z, double velocityX, double velocityY,
                   double velocityZ, double targetY, double gravity, double drag, int maxTicks);

    /** 判断候选者以给定水平速度能否在预测时间内到达落点，不访问世界或导航系统。 */
    boolean canReach(Result result, double x, double z, double speedPerTick, double tolerance);

    /** 预测落点与 tick 数；未到达时 tick 为负一且位置为模拟终点。 */
    final class Result {
        public final double x;
        public final double y;
        public final double z;
        public final int ticks;

        public Result(double x, double y, double z, int ticks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticks = ticks;
        }
    }
}
