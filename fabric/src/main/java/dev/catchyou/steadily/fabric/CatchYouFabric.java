package dev.catchyou.steadily.fabric;

import dev.catchyou.steadily.internal.RescueConfig;
import java.io.IOException;
import java.nio.file.Paths;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CatchYouFabric implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("catch-you-steadily");

    @Override
    public void onInitializeServer() {
        final FabricFallRescue rescue;
        try {
            rescue = new FabricFallRescue(RescueConfig.load(Paths.get("config", "catch-you-steadily.properties")));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load the rescue configuration", exception);
            throw new IllegalStateException("Could not initialize rescue gameplay", exception);
        }
        ServerTickEvents.START_SERVER_TICK.register(rescue::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer && source.is(DamageTypeTags.IS_FALL)) {
                return !rescue.catchAtFall((ServerPlayer)entity);
            }
            return true;
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> rescue.remove(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            rescue.stop();
            LOGGER.info("Rescue gameplay stopped");
        });
    }
}
