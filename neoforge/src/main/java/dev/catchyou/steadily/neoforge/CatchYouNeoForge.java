package dev.catchyou.steadily.neoforge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("catch_you_steadily")
public final class CatchYouNeoForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("catch-you-steadily");
    private NeoForgeFallRescue rescue;

    public CatchYouNeoForge(ModContainer container) {
        String filename = "catch_you_steadily-common.toml";
        Path config = FMLPaths.CONFIGDIR.get().resolve(filename);
        Path defaults = FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve(filename);
        try {
            NeoForgeConfig.validate(Files.exists(config) ? config : defaults);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not load the rescue configuration", exception);
            throw new IllegalStateException("Could not initialize rescue gameplay", exception);
        }
        container.registerConfig(ModConfig.Type.COMMON, NeoForgeConfig.SPEC);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            rescue = new NeoForgeFallRescue(NeoForgeConfig.snapshot());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not initialize rescue gameplay", exception);
            throw exception;
        }
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
