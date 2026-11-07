package dev.catchyou.steadily.legacy1122;

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
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = "catch_you_steadily", name = "Catch You Steadily", version = "0.1.0",
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public final class CatchYouForge1122 {
    private static final Logger LOGGER = LogManager.getLogger("catch-you-steadily");
    private static final int PREDICTION_INTERVAL = 5;
    private static final int GOAL_REPATH_INTERVAL = 10;
    private static final double FOLLOW_SPEED = 1.2D;
    private static final double RESCAN_HORIZONTAL_DISTANCE_SQUARED = 9.0D;
    private static final double RESCAN_VERTICAL_DISTANCE = 2.0D;
    private static final double LANDING_CONTACT_DISTANCE_SQUARED = 2.25D;
    private final RescueConfig config;
    private final FallTrajectory trajectory = new FallTrajectory();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, MotionSample> previousPositions = new HashMap<>();
    private long tick;

    public CatchYouForge1122() {
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
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUniqueID();
            Session session = sessions.get(playerId);
            Vec3d velocity;
            if (session != null || !player.onGround || player.fallDistance > 0.0F) {
                velocity = sampleMotion(player);
            } else {
                previousPositions.remove(playerId);
                velocity = new Vec3d(player.motionX, player.motionY, player.motionZ);
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

    private Vec3d sampleMotion(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        Vec3d current = new Vec3d(player.posX, player.posY, player.posZ);
        MotionSample previous = previousPositions.get(playerId);
        previousPositions.put(playerId, new MotionSample(current, tick,
                previous == null ? tick - PREDICTION_INTERVAL : previous.lastPredictionTick));
        if (previous == null) {
            return new Vec3d(player.motionX, player.motionY, player.motionZ);
        }
        double dx = current.x - previous.position.x;
        double dy = current.y - previous.position.y;
        double dz = current.z - previous.position.z;
        return dx * dx + dy * dy + dz * dz > 1.0E-8D
                ? new Vec3d(dx, dy, dz) : new Vec3d(player.motionX, player.motionY, player.motionZ);
    }

    private Session begin(EntityPlayerMP player, Vec3d velocity) {
        if (player.isDead || !player.isEntityAlive() || player.onGround || velocity.y >= 0.0D) {
            return null;
        }
        FallTrajectory.Result landing = predict(player, velocity);
        if (!landing.exists()) {
            return null;
        }
        Session session = new Session(player, (WorldServer)player.world, landing);
        if (!rescan(session)) {
            return null;
        }
        return session;
    }

    private boolean update(Session session, Vec3d velocity) {
        EntityPlayerMP player = session.player;
        if (player.isDead || !player.isEntityAlive() || player.world != session.world) {
            return false;
        }
        removeUnreachable(session);
        if (session.mountedEntity != null) {
            if (player.getRidingEntity() != session.mountedEntity || session.mountedEntity.isDead
                    || !session.mountedEntity.isEntityAlive() || session.mountedEntity.world != session.world
                    || !isLoaded(session.mountedEntity, session.world)) {
                return false;
            }
            if (session.mountedEntity.onGround || !session.world.getCollisionBoxes(session.mountedEntity,
                    session.mountedEntity.getEntityBoundingBox().offset(0, -0.05D, 0)).isEmpty()) {
                if (!config.keepRiding()) {
                    player.dismountRidingEntity();
                    LOGGER.info("Player {} safely dismounted", player.getUniqueID());
                }
                player.fallDistance = 0.0F;
                session.mountedEntity = null;
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

    private FallTrajectory.Result predict(EntityPlayerMP player, Vec3d velocity) {
        return trajectory.predict(player.posX, player.posY, player.posZ, velocity.x, velocity.y,
                velocity.z, player.fallDistance, new FallTrajectory.CollisionProbe() {
                    @Override
                    public FallTrajectory.Movement move(double x, double y, double z,
                                                         double dx, double dy, double dz) {
                        AxisAlignedBB box = player.getEntityBoundingBox().offset(
                                x - player.posX, y - player.posY, z - player.posZ);
                        List<AxisAlignedBB> collisions = player.world.getCollisionBoxes(player, box.expand(dx, dy, dz));
                        double cy = dy;
                        for (AxisAlignedBB collision : collisions) {
                            cy = collision.calculateYOffset(box, cy);
                        }
                        boolean liquid = player.world.containsAnyLiquid(box.expand(0, cy, 0));
                        box = box.offset(0, cy, 0);
                        double cx = dx;
                        for (AxisAlignedBB collision : collisions) {
                            cx = collision.calculateXOffset(box, cx);
                        }
                        liquid |= player.world.containsAnyLiquid(box.expand(cx, 0, 0));
                        box = box.offset(cx, 0, 0);
                        double cz = dz;
                        for (AxisAlignedBB collision : collisions) {
                            cz = collision.calculateZOffset(box, cz);
                        }
                        liquid |= player.world.containsAnyLiquid(box.expand(0, 0, cz));
                        return new FallTrajectory.Movement(cx, cy, cz, liquid);
                    }
                });
    }

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP
                && catchAtFall((EntityPlayerMP)event.getEntityLiving())) {
            event.setCanceled(true);
        }
    }

    private boolean catchAtFall(EntityPlayerMP player) {
        Session session = sessions.get(player.getUniqueID());
        if (session == null || session.fallDamageHandled) {
            return false;
        }
        if (session.mountedEntity == null || player.getRidingEntity() != session.mountedEntity) {
            return false;
        }
        session.fallDamageHandled = true;
        return true;
    }

    private boolean tryMount(Session session, Vec3d velocity) {
        EntityPlayerMP player = session.player;
        for (FollowGoal goal : session.followers) {
            EntityLiving mob = goal.mob;
            if (mob.isDead || !mob.isEntityAlive() || mob.world != session.world
                    || !isLoaded(mob, session.world)) {
                continue;
            }
            double dx = mob.posX - session.landing.x;
            double dz = mob.posZ - session.landing.z;
            if (dx * dx + dz * dz > LANDING_CONTACT_DISTANCE_SQUARED) {
                continue;
            }
            AxisAlignedBB mobBox = mob.getEntityBoundingBox();
            if (!trajectory.reachesSeat(player.posX, player.posY, player.posZ, player.width,
                    velocity.x, velocity.y, velocity.z, mobBox.minX, mobBox.maxX, mobBox.minZ, mobBox.maxZ,
                    mobBox.maxY)) {
                continue;
            }
            if (player.startRiding(mob)) {
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
        AxisAlignedBB searchBox = new AxisAlignedBB(session.landing.x - radius, session.landing.y - verticalRange,
                session.landing.z - radius, session.landing.x + radius,
                session.landing.y + verticalRange, session.landing.z + radius);
        List<EntityLiving> candidates;
        try {
            candidates = session.world.getEntitiesWithinAABB(EntityLiving.class, searchBox);
        } catch (RuntimeException exception) {
            LOGGER.error("Could not rescan rescue candidates", exception);
            return false;
        }
        clearGoals(session);
        double radiusSquared = radius * radius;
        for (EntityLiving mob : candidates) {
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
        if (mob.isDead || !mob.isEntityAlive() || mob.isRiding() || mob.world != world) {
            return false;
        }
        String entityId = EntityList.getKey(mob).toString();
        return !config.excludes(entityId, mob.isCreatureType(EnumCreatureType.MONSTER, false));
    }

    private boolean isLoaded(EntityLiving mob, WorldServer world) {
        return mob.addedToChunk && world.getChunkProvider().getLoadedChunk(mob.chunkCoordX, mob.chunkCoordZ) != null
                && world.getEntityByID(mob.getEntityId()) == mob;
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
            if (goal.mob.isDead || !goal.mob.isEntityAlive() || goal.mob.world != session.world
                    || !isLoaded(goal.mob, session.world) || goal.unreachable) {
                goal.mob.tasks.removeTask(goal);
                iterator.remove();
            }
        }
    }

    private void clear(Session session) {
        if (session.mountedEntity != null && session.player.getRidingEntity() == session.mountedEntity) {
            boolean landed = session.player.onGround || session.mountedEntity.onGround;
            session.player.dismountRidingEntity();
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

    private static final class MotionSample {
        private final Vec3d position;
        private final long lastSeenTick;
        private long lastPredictionTick;

        private MotionSample(Vec3d position, long lastSeenTick, long lastPredictionTick) {
            this.position = position;
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
        private EntityLiving mountedEntity;
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
        public boolean shouldContinueExecuting() {
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
            mob.getNavigator().clearPath();
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
                mob.getNavigator().clearPath();
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
