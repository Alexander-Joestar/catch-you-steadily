package dev.catchyou.steadily.api;

import dev.catchyou.steadily.internal.RescueTasksImpl;
import java.util.UUID;

/**
 * 跟踪玩家的接住任务及其剩余时间。实例只能在服务端主线程使用，不访问游戏世界。
 */
public interface RescueTasks {
    /** 创建空任务集合。 */
    static RescueTasks create() {
        return new RescueTasksImpl();
    }

    /** 为玩家开始任务，替换该玩家原有任务。ticks 必须大于零。 */
    void start(UUID player, int ticks);

    /** 推进一个服务端 tick，移除超时任务。 */
    void tick();

    /** 玩家任务完成或取消时移除任务。 */
    void remove(UUID player);

    /** 查询玩家是否存在尚未超时的任务。 */
    boolean contains(UUID player);

    /** 服务端关闭时移除全部任务。 */
    void clear();
}
