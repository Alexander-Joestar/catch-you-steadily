package dev.catchyou.steadily.legacy1122;

import dev.catchyou.steadily.api.FallPrediction;
import dev.catchyou.steadily.api.RescueTasks;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

@Mod(modid = "catch_you_steadily", name = "Catch You Steadily", version = "0.1.0", acceptableRemoteVersions = "*")
public final class CatchYouForge1122 {
    private final RescueTasks tasks = RescueTasks.create();
    private final FallPrediction prediction = FallPrediction.create();

    public CatchYouForge1122() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tasks.tick();
        for (Object object : net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getPlayerList().getPlayers()) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (player.onGround) {
                tasks.remove(player.getUniqueID());
                continue;
            }
            if (player.motionY >= 0 || tasks.contains(player.getUniqueID())) continue;
            WorldServer world = (WorldServer) player.world;
            int ground = world.getHeight((int) Math.floor(player.posX), (int) Math.floor(player.posZ));
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
