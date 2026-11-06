package dev.catchyou.steadily.legacy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import dev.catchyou.steadily.api.FallPrediction;
import dev.catchyou.steadily.api.RescueTasks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

@Mod(modid = "catch_you_steadily", name = "Catch You Steadily", version = "0.1.0", acceptableRemoteVersions = "*")
public final class CatchYouForge1710 {
    private final RescueTasks tasks = RescueTasks.create();
    private final FallPrediction prediction = FallPrediction.create();

    @Mod.EventHandler
    public void serverStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tasks.tick();
        for (Object object : FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (player.onGround) {
                tasks.remove(player.getUniqueID());
                continue;
            }
            if (player.motionY >= 0 || tasks.contains(player.getUniqueID())) continue;
            WorldServer world = (WorldServer) player.worldObj;
            int ground = world.getHeightValue((int) Math.floor(player.posX), (int) Math.floor(player.posZ));
            FallPrediction.Result result = prediction.predict(player.posX, player.posY, player.posZ,
                    player.motionX, player.motionY, player.motionZ, ground, 0.08, 0.98, 100);
            if (result.ticks > 0) tasks.start(player.getUniqueID(), result.ticks);
        }
    }

    @SubscribeEvent
    public void onServerStopping(FMLServerStoppingEvent event) {
        tasks.clear();
    }
}
