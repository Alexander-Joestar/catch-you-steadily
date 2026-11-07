package dev.catchyou.steadily.forge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("catch_you_steadily")
public final class CatchYouForge {
    private static final Logger LOGGER = LoggerFactory.getLogger("catch-you-steadily");
    private ForgeFallRescue rescue;

    public CatchYouForge() {
        String filename = "catch_you_steadily-common.toml";
        Path config = FMLPaths.CONFIGDIR.get().resolve(filename);
        Path defaults = FMLPaths.GAMEDIR.get().resolve(FMLConfig.getConfigValue(FMLConfig.ConfigValue.DEFAULT_CONFIG_PATH)).resolve(filename);
        try {
            ForgeConfig.validate(Files.exists(config) ? config : defaults);
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Could not load the rescue configuration", exception);
            throw new IllegalStateException("Could not initialize rescue gameplay", exception);
        }
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ForgeConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        try {
            rescue = new ForgeFallRescue(ForgeConfig.snapshot());
        } catch (RuntimeException exception) {
            LOGGER.error("Could not initialize rescue gameplay", exception);
            throw exception;
        }
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
