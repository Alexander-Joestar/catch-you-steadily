package dev.catchyou.steadily.internal;

import dev.catchyou.steadily.api.RescueTasks;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class RescueTasksImpl implements RescueTasks {
    private final Map<UUID, Integer> tasks = new HashMap<>();

    @Override
    public void start(UUID player, int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Task duration must be positive");
        }
        tasks.put(Objects.requireNonNull(player, "player"), ticks);
    }

    @Override
    public void tick() {
        Iterator<Map.Entry<UUID, Integer>> iterator = tasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> task = iterator.next();
            int remaining = task.getValue() - 1;
            if (remaining == 0) {
                iterator.remove();
            } else {
                task.setValue(remaining);
            }
        }
    }

    @Override
    public void remove(UUID player) {
        tasks.remove(player);
    }

    @Override
    public boolean contains(UUID player) {
        return tasks.containsKey(player);
    }

    @Override
    public void clear() {
        tasks.clear();
    }
}
