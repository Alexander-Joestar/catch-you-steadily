package dev.catchyou.steadily.forge;

import dev.catchyou.steadily.api.FallPrediction;
import dev.catchyou.steadily.api.RescueTasks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;

@Mod("catch_you_steadily")
public final class CatchYouForge {
    private final RescueTasks tasks = RescueTasks.create();
    private final FallPrediction prediction = FallPrediction.create();

    public CatchYouForge() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tasks.tick();
        if (event.getServer() == null) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (player.onGround()) {
                tasks.remove(player.getUUID());
                continue;
            }
            Vec3 velocity = player.getDeltaMovement();
            if (velocity.y >= 0 || tasks.contains(player.getUUID())) continue;
            int ground = player.serverLevel().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    player.blockPosition().getX(), player.blockPosition().getZ());
            FallPrediction.Result result = prediction.predict(player.getX(), player.getY(), player.getZ(),
                    velocity.x, velocity.y, velocity.z, ground, 0.08, 0.98, 100);
            if (result.ticks > 0) {
                tasks.start(player.getUUID(), result.ticks);
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        tasks.clear();
    }
}
