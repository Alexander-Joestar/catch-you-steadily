package dev.catchyou.steadily.neoforge;

import dev.catchyou.steadily.internal.RescueConfig;
import java.io.IOException;
import java.nio.file.Paths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("catch_you_steadily")
public final class CatchYouNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("catch-you-steadily");
    private final NeoForgeFallRescue rescue;

    public CatchYouNeoForge() {
        try {
            rescue = new NeoForgeFallRescue(RescueConfig.load(Paths.get("config", "catch-you-steadily.properties")));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load the rescue configuration", exception);
            throw new IllegalStateException("Could not initialize rescue gameplay", exception);
        }
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (server != null) {
            rescue.tick(server);
        }
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer
                && rescue.catchAtFall((ServerPlayer)event.getEntity())) {
            event.setDamageMultiplier(0.0F);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        rescue.stop();
    }
}
