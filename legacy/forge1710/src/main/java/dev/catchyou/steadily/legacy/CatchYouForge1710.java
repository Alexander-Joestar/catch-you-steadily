package dev.catchyou.steadily.legacy;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import dev.catchyou.steadily.internal.FallTrajectory;
import dev.catchyou.steadily.internal.RescueConfig;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "catch_you_steadily", name = "Catch You Steadily", version = "0.1.0",
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.7.10]")
public final class CatchYouForge1710 {
    private static final Logger LOGGER = LogManager.getLogger("catch-you-steadily");
    private static final int PREDICTION_INTERVAL = 5;
    private static final int GOAL_REPATH_INTERVAL = 10;
    private static final double FOLLOW_SPEED = 1.2D;
    private static final double RESCAN_HORIZONTAL_DISTANCE_SQUARED = 9.0D;
    private static final double RESCAN_VERTICAL_DISTANCE = 2.0D;
    private static final double LANDING_CONTACT_DISTANCE_SQUARED = 2.25D;
    private final FallTrajectory trajectory = new FallTrajectory();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, MotionSample> previousPositions = new HashMap<>();
    private RescueConfig config;
    private long tick;

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        try {
            config = RescueConfig.load(Paths.get("config", "catch-you-steadily.properties"));
        } catch (IOException | IllegalArgumentException exception) {
            LOGGER.error("Could not load the rescue configuration", exception);
            throw new IllegalStateException("Could not initialize rescue gameplay", exception);
        }
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            tick(server);
        }
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        stop();
    }

    private void tick(MinecraftServer server) {
        tick++;
        for (Object object : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP)object;
            UUID playerId = player.getUniqueID();
            Session session = sessions.get(playerId);
            Motion velocity;
            if (session != null || !player.onGround || player.fallDistance > 0.0F) {
                velocity = sampleMotion(player);
            } else {
                previousPositions.remove(playerId);
                velocity = new Motion(player.motionX, player.motionY, player.motionZ);
            }
            if (session == null) {
                MotionSample sample = previousPositions.get(playerId);
                if (player.onGround || player.fallDistance < 0.25F || velocity.y >= 0.0D
                        || tick - sample.lastPredictionTick < PREDICTION_INTERVAL) {
                    continue;
                }
                sample.lastPredictionTick = tick;
                session = begin(player, velocity);
                if (session == null) {
                    continue;
                }
                session.lastPredictionTick = tick;
                sessions.put(playerId, session);
            }
            session.lastSeenTick = tick;
            if (!update(session, velocity)) {
                clear(session);
                sessions.remove(playerId);
            }
        }
        Iterator<Map.Entry<UUID, MotionSample>> positions = previousPositions.entrySet().iterator();
        while (positions.hasNext()) {
            if (positions.next().getValue().lastSeenTick != tick) {
                positions.remove();
            }
        }
        Iterator<Session> iterator = sessions.values().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.lastSeenTick != tick) {
                clearGoals(session);
                iterator.remove();
            }
        }
    }

    private Motion sampleMotion(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        double x = player.posX;
        double y = player.posY;
        double z = player.posZ;
        MotionSample previous = previousPositions.get(playerId);
        previousPositions.put(playerId, new MotionSample(x, y, z, tick,
                previous == null ? tick - PREDICTION_INTERVAL : previous.lastPredictionTick));
        if (previous == null) {
            return new Motion(player.motionX, player.motionY, player.motionZ);
        }
        double dx = x - previous.x;
        double dy = y - previous.y;
        double dz = z - previous.z;
        return dx * dx + dy * dy + dz * dz > 1.0E-8D
                ? new Motion(dx, dy, dz) : new Motion(player.motionX, player.motionY, player.motionZ);
    }

    private Session begin(EntityPlayerMP player, Motion velocity) {
        if (player.isDead || !player.isEntityAlive() || player.onGround || velocity.y >= 0.0D) {
            return null;
        }
        FallTrajectory.Result landing = predict(player, velocity);
        if (!landing.exists()) {
            return null;
        }
        Session session = new Session(player, (WorldServer)player.worldObj, landing);
        if (!rescan(session)) {
            return null;
        }
        return session;
    }

    private boolean update(Session session, Motion velocity) {
        EntityPlayerMP player = session.player;
        if (player.isDead || !player.isEntityAlive() || player.worldObj != session.world) {
            return false;
        }
        removeUnreachable(session);
        if (session.mountedEntity != null) {
            if (player.ridingEntity != session.mountedEntity || session.mountedEntity.isDead
                    || !session.mountedEntity.isEntityAlive() || session.mountedEntity.worldObj != session.world
                    || !isLoaded(session.mountedEntity, session.world)) {
                return false;
            }
            if (session.mountedEntity.onGround || !session.world.getCollidingBoundingBoxes(session.mountedEntity,
                    session.mountedEntity.getBoundingBox().offset(0, -0.05D, 0)).isEmpty()) {
                player.mountEntity(null);
                player.fallDistance = 0.0F;
                LOGGER.info("Player {} safely dismounted", player.getUniqueID());
                return false;
            }
            return true;
        }
        if (player.onGround || velocity.y > 0.0D || player.isInWater()) {
            return false;
        }
        if (velocity.y < 0.0D && tryMount(session, velocity)) {
            return true;
        }
        if (velocity.y < 0.0D && tick - session.lastPredictionTick >= PREDICTION_INTERVAL) {
            session.lastPredictionTick = tick;
            FallTrajectory.Result landing = predict(player, velocity);
            if (!landing.exists()) {
                return false;
            }
            boolean rescan = needsRescan(session, landing);
            session.landing = landing;
            if (rescan && rescan(session)) {
                session.lastRescanLanding = landing;
            } else {
                updateGoalTargets(session);
            }
        }
        removeUnreachable(session);
        return true;
    }

    private FallTrajectory.Result predict(EntityPlayerMP player, Motion velocity) {
        return trajectory.predict(player.posX, player.posY, player.posZ, velocity.x, velocity.y,
                velocity.z, player.fallDistance, new FallTrajectory.CollisionProbe() {
                    @Override
                    public FallTrajectory.Movement move(double x, double y, double z,
                                                         double dx, double dy, double dz) {
                        AxisAlignedBB box = player.boundingBox.getOffsetBoundingBox(
                                x - player.posX, y - player.posY, z - player.posZ);
                        List<?> collisions = player.worldObj.getCollidingBoundingBoxes(player, box.addCoord(dx, dy, dz));
                        double cy = dy;
                        for (Object collision : collisions) {
                            cy = ((AxisAlignedBB)collision).calculateYOffset(box, cy);
                        }
                        boolean liquid = player.worldObj.isAnyLiquid(box.addCoord(0, cy, 0));
                        box.offset(0, cy, 0);
                        double cx = dx;
                        for (Object collision : collisions) {
                            cx = ((AxisAlignedBB)collision).calculateXOffset(box, cx);
                        }
                        liquid |= player.worldObj.isAnyLiquid(box.addCoord(cx, 0, 0));
                        box.offset(cx, 0, 0);
                        double cz = dz;
                        for (Object collision : collisions) {
                            cz = ((AxisAlignedBB)collision).calculateZOffset(box, cz);
                        }
                        liquid |= player.worldObj.isAnyLiquid(box.addCoord(0, 0, cz));
                        return new FallTrajectory.Movement(cx, cy, cz, liquid);
                    }
                });
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (event.entityLiving instanceof EntityPlayerMP
                && catchAtFall((EntityPlayerMP)event.entityLiving)) {
            event.setCanceled(true);
        }
    }

    private boolean catchAtFall(EntityPlayerMP player) {
        Session session = sessions.get(player.getUniqueID());
        if (session == null || session.fallDamageHandled) {
            return false;
        }
        if (session.mountedEntity == null || player.ridingEntity != session.mountedEntity) {
            return false;
        }
        session.fallDamageHandled = true;
        return true;
    }

    private boolean tryMount(Session session, Motion velocity) {
        EntityPlayerMP player = session.player;
        for (FollowGoal goal : session.followers) {
            EntityLiving mob = goal.mob;
            if (mob.isDead || !mob.isEntityAlive() || mob.worldObj != session.world
                    || !isLoaded(mob, session.world) || mob.riddenByEntity != null) {
                continue;
            }
            double dx = mob.posX - session.landing.x;
            double dz = mob.posZ - session.landing.z;
            if (dx * dx + dz * dz > LANDING_CONTACT_DISTANCE_SQUARED) {
                continue;
            }
            AxisAlignedBB mobBox = mob.getBoundingBox();
            if (!trajectory.reachesSeat(player.posX, player.posY, player.posZ, player.width,
                    velocity.x, velocity.y, velocity.z, mobBox.minX, mobBox.maxX, mobBox.minZ, mobBox.maxZ,
                    mobBox.maxY)) {
                continue;
            }
            player.mountEntity(mob);
            if (player.ridingEntity == mob) {
                session.mountedEntity = mob;
                player.fallDistance = 0.0F;
                clearGoals(session);
                return true;
            }
        }
        return false;
    }

    private boolean needsRescan(Session session, FallTrajectory.Result landing) {
        double dx = landing.x - session.lastRescanLanding.x;
        double dz = landing.z - session.lastRescanLanding.z;
        return dx * dx + dz * dz >= RESCAN_HORIZONTAL_DISTANCE_SQUARED
                || Math.abs(landing.y - session.lastRescanLanding.y) >= RESCAN_VERTICAL_DISTANCE;
    }

    private boolean rescan(Session session) {
        double radius = config.attractionRadius();
        double verticalRange = config.verticalRange();
        AxisAlignedBB searchBox = AxisAlignedBB.getBoundingBox(session.landing.x - radius,
                session.landing.y - verticalRange, session.landing.z - radius,
                session.landing.x + radius, session.landing.y + verticalRange, session.landing.z + radius);
        List candidates;
        try {
            candidates = session.world.getEntitiesWithinAABB(EntityLiving.class, searchBox);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not rescan rescue candidates", exception);
            return false;
        }
        clearGoals(session);
        double radiusSquared = radius * radius;
        for (Object object : candidates) {
            EntityLiving mob = (EntityLiving)object;
            if (!eligible(mob, session.world)) {
                continue;
            }
            double dx = mob.posX - session.landing.x;
            double dz = mob.posZ - session.landing.z;
            if (dx * dx + dz * dz > radiusSquared || Math.abs(mob.posY - session.landing.y) > verticalRange) {
                continue;
            }
            FollowGoal goal = new FollowGoal(mob, session);
            goal.setTarget(session.landing.x, session.landing.z);
            if (goal.repath()) {
                mob.tasks.addTask(-1, goal);
                session.followers.add(goal);
            }
        }
        return true;
    }

    private boolean eligible(EntityLiving mob, WorldServer world) {
        if (mob.isDead || !mob.isEntityAlive() || mob.ridingEntity != null || mob.worldObj != world) {
            return false;
        }
        String entityId = EntityList.getEntityString(mob);
        String registryId = normalizeEntityId(entityId);
        boolean hostile = mob.isCreatureType(EnumCreatureType.monster, false);
        return !config.excludes(entityId, hostile) && !config.excludes(registryId, hostile);
    }

    private String normalizeEntityId(String entityId) {
        if (entityId == null || entityId.indexOf(':') >= 0) {
            return entityId;
        }
        int separator = entityId.indexOf('.');
        if (separator >= 0) {
            return entityId.substring(0, separator) + ':' + entityId.substring(separator + 1);
        }
        return "minecraft:" + entityId.toLowerCase(java.util.Locale.ROOT);
    }

    private boolean isLoaded(Entity entity, WorldServer world) {
        return entity.addedToChunk
                && world.getChunkProvider().chunkExists(entity.chunkCoordX, entity.chunkCoordZ)
                && world.getEntityByID(entity.getEntityId()) == entity;
    }

    private void updateGoalTargets(Session session) {
        for (FollowGoal goal : session.followers) {
            goal.setTarget(session.landing.x, session.landing.z);
        }
    }

    private void removeUnreachable(Session session) {
        Iterator<FollowGoal> iterator = session.followers.iterator();
        while (iterator.hasNext()) {
            FollowGoal goal = iterator.next();
            if (goal.mob.isDead || !goal.mob.isEntityAlive() || goal.mob.worldObj != session.world
                    || !isLoaded(goal.mob, session.world) || goal.unreachable) {
                goal.mob.tasks.removeTask(goal);
                iterator.remove();
            }
        }
    }

    private void clear(Session session) {
        if (session.mountedEntity != null && session.player.ridingEntity == session.mountedEntity) {
            boolean landed = session.player.onGround || session.mountedEntity.onGround;
            session.player.mountEntity(null);
            session.player.fallDistance = 0.0F;
            if (landed) {
                LOGGER.info("Player {} safely dismounted", session.player.getUniqueID());
            }
        }
        clearGoals(session);
    }

    private void clearGoals(Session session) {
        for (FollowGoal goal : session.followers) {
            goal.mob.tasks.removeTask(goal);
        }
        session.followers.clear();
    }

    private void stop() {
        for (Session session : sessions.values()) {
            clear(session);
        }
        sessions.clear();
        previousPositions.clear();
    }

    private static final class Motion {
        private final double x;
        private final double y;
        private final double z;

        private Motion(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final class MotionSample {
        private final double x;
        private final double y;
        private final double z;
        private final long lastSeenTick;
        private long lastPredictionTick;

        private MotionSample(double x, double y, double z, long lastSeenTick, long lastPredictionTick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastSeenTick = lastSeenTick;
            this.lastPredictionTick = lastPredictionTick;
        }
    }

    private static final class Session {
        private final EntityPlayerMP player;
        private final WorldServer world;
        private final List<FollowGoal> followers = new ArrayList<>();
        private FallTrajectory.Result landing;
        private FallTrajectory.Result lastRescanLanding;
        private Entity mountedEntity;
        private long lastPredictionTick;
        private long lastSeenTick;
        private boolean fallDamageHandled;

        private Session(EntityPlayerMP player, WorldServer world, FallTrajectory.Result landing) {
            this.player = player;
            this.world = world;
            this.landing = landing;
            this.lastRescanLanding = landing;
        }
    }

    private static final class FollowGoal extends EntityAIBase {
        private final EntityLiving mob;
        private final Session session;
        private double targetX;
        private double targetZ;
        private double lastPathX = Double.NaN;
        private double lastPathZ = Double.NaN;
        private int repathDelay;
        private boolean unreachable;

        private FollowGoal(EntityLiving mob, Session session) {
            this.mob = mob;
            this.session = session;
            setMutexBits(1);
        }

        @Override
        public boolean shouldExecute() {
            return !unreachable && session.player.isEntityAlive() && session.mountedEntity == null;
        }

        @Override
        public boolean continueExecuting() {
            return shouldExecute() && mob.isEntityAlive() && !mob.isDead;
        }

        @Override
        public void updateTask() {
            if (repathDelay-- <= 0) {
                repathDelay = GOAL_REPATH_INTERVAL;
                double dx = targetX - lastPathX;
                double dz = targetZ - lastPathZ;
                if (Double.isNaN(lastPathX) || dx * dx + dz * dz >= 4.0D
                        || (mob.getNavigator().noPath() && distanceToTargetSquared() > 2.25D)) {
                    repath();
                }
            }
        }

        @Override
        public void resetTask() {
            mob.getNavigator().clearPathEntity();
        }

        private void setTarget(double x, double z) {
            targetX = x;
            targetZ = z;
        }

        private boolean repath() {
            lastPathX = targetX;
            lastPathZ = targetZ;
            double contactDistance = (mob.width + session.player.width) / 2.0D;
            if (Math.abs(mob.posX - targetX) < contactDistance
                    && Math.abs(mob.posZ - targetZ) < contactDistance) {
                mob.getNavigator().clearPathEntity();
                return true;
            }
            unreachable = !mob.getNavigator().tryMoveToXYZ(targetX, mob.posY, targetZ, FOLLOW_SPEED);
            return !unreachable;
        }

        private double distanceToTargetSquared() {
            double dx = mob.posX - targetX;
            double dz = mob.posZ - targetZ;
            return dx * dx + dz * dz;
        }
    }
}
