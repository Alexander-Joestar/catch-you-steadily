package dev.catchyou.steadily.fabric;

import dev.catchyou.steadily.api.FallPrediction;
import dev.catchyou.steadily.api.RescueTasks;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CatchYouFabric implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("catch-you-steadily");
    private final RescueTasks tasks = RescueTasks.create();
    private final FallPrediction prediction = FallPrediction.create();
    private boolean tickSeen;

    @Override
    public void onInitializeServer() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!tickSeen) {
                tickSeen = true;
                LOGGER.info("Catch You Steadily server tick active");
            }
            tasks.tick();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.onGround()) {
                    tasks.remove(player.getUUID());
                    continue;
                }
                Vec3 velocity = player.getDeltaMovement();
                if (velocity.y >= 0 || tasks.contains(player.getUUID())) {
                    continue;
                }
                int ground = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING,
                        player.blockPosition().getX(), player.blockPosition().getZ());
                FallPrediction.Result result = prediction.predict(player.getX(), player.getY(), player.getZ(),
                        velocity.x, velocity.y, velocity.z, ground, 0.08, 0.98, 100);
                if (result.ticks > 0 && player.getY() - result.y > 3) {
                    tasks.start(player.getUUID(), result.ticks);
                }
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            tasks.clear();
            tickSeen = false;
            LOGGER.info("Catch You Steadily server tasks cleared");
        });
    }
}
