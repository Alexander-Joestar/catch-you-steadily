package dev.catchyou.steadily.forge;

import dev.catchyou.steadily.internal.RescueConfig;
import java.io.IOException;
import java.nio.file.Paths;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("catch_you_steadily")
public final class CatchYouForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("catch-you-steadily");
    private final ForgeFallRescue rescue;

    public CatchYouForge() {
        try {
            rescue = new ForgeFallRescue(RescueConfig.load(Paths.get("config", "catch-you-steadily.properties")));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load the rescue configuration", exception);
            throw new IllegalStateException("Could not initialize rescue gameplay", exception);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            MinecraftServer server = event.getServer();
            if (server != null) {
                rescue.tick(server);
            }
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
