package dev.catchyou.steadily.fabric;

import dev.catchyou.steadily.internal.FallTrajectory;
import dev.catchyou.steadily.internal.RescueConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class FabricFallRescue {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("catch-you-steadily");
    private static final int PREDICTION_INTERVAL = 5;
    private static final int GOAL_REPATH_INTERVAL = 10;
    private static final double FOLLOW_SPEED = 1.2;
    private static final double RESCAN_HORIZONTAL_DISTANCE_SQUARED = 9;
    private static final double RESCAN_VERTICAL_DISTANCE = 2;
    private static final double LANDING_CONTACT_DISTANCE_SQUARED = 2.25;
    private final RescueConfig config;
    private final FallTrajectory trajectory = new FallTrajectory();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, MotionSample> previousPositions = new HashMap<>();
    private long tick;

    FabricFallRescue(RescueConfig config) {
        this.config = config;
    }

    void tick(MinecraftServer server) {
        tick++;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();
            Session session = sessions.get(playerId);
            MotionSample previous = previousPositions.get(playerId);
            Vec3 current = player.position();
            Vec3 previousPosition = previous == null ? current : previous.position;
            boolean sampling = session != null || !player.onGround() || player.fallDistance > 0;
            if (!sampling) {
                previousPositions.remove(playerId);
                continue;
            }
            MotionSample sample = new MotionSample(current, tick,
                    previous == null ? tick - PREDICTION_INTERVAL : previous.lastPredictionTick);
            Vec3 observed = current.subtract(previousPosition);
            Vec3 velocity = observed.lengthSqr() > 1.0E-8 ? observed : player.getDeltaMovement();
            try {
                if (session == null) {
                    if (player.onGround() || player.fallDistance < 0.25F || velocity.y >= 0
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
                if (!update(server, session, velocity)) {
                    clear(session);
                    sessions.remove(playerId);
                }
            } finally {
                previousPositions.put(playerId, sample);
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

    boolean catchAtFall(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session == null || session.fallDamageHandled) {
            return false;
        }
        if (session.mountedEntity == null || player.getVehicle() != session.mountedEntity) {
            return false;
        }
        session.fallDamageHandled = true;
        return true;
    }

    void remove(ServerPlayer player) {
        UUID playerId = player.getUUID();
        Session session = sessions.remove(playerId);
        previousPositions.remove(playerId);
        if (session != null) {
            clear(session);
        }
    }

    void stop() {
        for (Session session : sessions.values()) {
            clear(session);
        }
        sessions.clear();
        previousPositions.clear();
    }

    private Session begin(ServerPlayer player, Vec3 velocity) {
        if (!player.isAlive() || player.isRemoved() || player.onGround() || velocity.y >= 0) {
            return null;
        }
        FallTrajectory.Result landing = predict(player, velocity);
        if (!landing.exists()) {
            return null;
        }
        Session session = new Session(player, player.serverLevel(), landing);
        rescan(session);
        LOGGER.info("Fall rescue started for {} with {} navigable mobs", player.getGameProfile().getName(),
                session.followers.size());
        return session;
    }

    private boolean update(MinecraftServer server, Session session, Vec3 velocity) {
        ServerPlayer player = session.player;
        if (player.isRemoved() || !player.isAlive()
                || player.serverLevel() != session.level || player.getServer() != server) {
            return false;
        }
        if (session.mountedEntity != null) {
            if (player.getVehicle() != session.mountedEntity || session.mountedEntity.isRemoved()
                    || !session.mountedEntity.isAlive() || session.mountedEntity.level() != session.level) {
                return false;
            }
            if (session.mountedEntity.onGround() || !session.level.noCollision(session.mountedEntity,
                    session.mountedEntity.getBoundingBox().move(0, -0.05, 0))) {
                player.stopRiding();
                player.resetFallDistance();
                LOGGER.info("Player {} safely dismounted", player.getGameProfile().getName());
                return false;
            }
            return true;
        }
        if (velocity.y > 0 || player.isInWaterOrBubble()) {
            return false;
        }
        if (player.onGround()) {
            return false;
        }
        if (velocity.y < 0 && tryMount(session, velocity)) {
            return true;
        }
        if (velocity.y < 0 && tick - session.lastPredictionTick >= PREDICTION_INTERVAL) {
            session.lastPredictionTick = tick;
            FallTrajectory.Result landing = predict(player, velocity);
            if (!landing.exists()) {
                return false;
            }
            if (needsRescan(session, landing)) {
                session.landing = landing;
                rescan(session);
            } else {
                session.landing = landing;
                updateGoalTargets(session);
            }
        }
        removeUnreachable(session);
        return true;
    }

    private FallTrajectory.Result predict(ServerPlayer player, Vec3 velocity) {
        return trajectory.predict(player.getX(), player.getY(), player.getZ(), velocity.x, velocity.y,
                velocity.z, player.fallDistance, new FallTrajectory.CollisionProbe() {
                    @Override
                    public FallTrajectory.Movement move(double x, double y, double z,
                                                         double dx, double dy, double dz) {
                        AABB box = player.getBoundingBox().move(x - player.getX(), y - player.getY(), z - player.getZ());
                        Vec3 clipped = net.minecraft.world.entity.Entity.collideBoundingBox(player,
                                new Vec3(dx, dy, dz), box, player.level(),
                                player.level().getEntityCollisions(player, box.expandTowards(dx, dy, dz)));
                        boolean liquid = player.level().containsAnyLiquid(box.expandTowards(0, clipped.y, 0));
                        box = box.move(0, clipped.y, 0);
                        if (Math.abs(clipped.x) < Math.abs(clipped.z)) {
                            liquid |= player.level().containsAnyLiquid(box.expandTowards(0, 0, clipped.z));
                            box = box.move(0, 0, clipped.z);
                            liquid |= player.level().containsAnyLiquid(box.expandTowards(clipped.x, 0, 0));
                        } else {
                            liquid |= player.level().containsAnyLiquid(box.expandTowards(clipped.x, 0, 0));
                            box = box.move(clipped.x, 0, 0);
                            liquid |= player.level().containsAnyLiquid(box.expandTowards(0, 0, clipped.z));
                        }
                        return new FallTrajectory.Movement(clipped.x, clipped.y, clipped.z, liquid);
                    }
                });
    }

    private boolean tryMount(Session session, Vec3 velocity) {
        ServerPlayer player = session.player;
        for (FollowGoal followGoal : session.followers) {
            Mob mob = followGoal.mob;
            if (!mob.isAlive() || mob.isRemoved() || mob.level() != session.level) {
                continue;
            }
            double distanceX = mob.getX() - session.landing.x;
            double distanceZ = mob.getZ() - session.landing.z;
            if (distanceX * distanceX + distanceZ * distanceZ > LANDING_CONTACT_DISTANCE_SQUARED) {
                continue;
            }
            AABB mobBox = mob.getBoundingBox();
            if (!trajectory.reachesSeat(player.getX(), player.getY(), player.getZ(), player.getBbWidth(),
                    velocity.x, velocity.y, velocity.z, mobBox.minX, mobBox.maxX, mobBox.minZ, mobBox.maxZ,
                    mobBox.maxY)) {
                continue;
            }
            if (player.startRiding(mob)) {
                session.mountedEntity = mob;
                player.resetFallDistance();
                clearGoals(session);
                LOGGER.info("Player {} was caught by {}", player.getGameProfile().getName(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
                return true;
            }
        }
        return false;
    }

    private boolean needsRescan(Session session, FallTrajectory.Result landing) {
        double distanceX = landing.x - session.landing.x;
        double distanceZ = landing.z - session.landing.z;
        return distanceX * distanceX + distanceZ * distanceZ >= RESCAN_HORIZONTAL_DISTANCE_SQUARED
                || Math.abs(landing.y - session.landing.y) >= RESCAN_VERTICAL_DISTANCE;
    }

    private void rescan(Session session) {
        pruneFollowers(session);
        double radius = config.attractionRadius();
        double verticalRange = config.verticalRange();
        AABB searchBox = new AABB(session.landing.x - radius, session.landing.y - verticalRange,
                session.landing.z - radius, session.landing.x + radius,
                session.landing.y + verticalRange, session.landing.z + radius);
        List<Mob> candidates = session.level.getEntitiesOfClass(Mob.class, searchBox,
                mob -> eligible(mob, session.level));
        double radiusSquared = radius * radius;
        for (Mob mob : candidates) {
            double dx = mob.getX() - session.landing.x;
            double dz = mob.getZ() - session.landing.z;
            if (dx * dx + dz * dz > radiusSquared
                    || Math.abs(mob.getY() - session.landing.y) > verticalRange
                    || hasFollower(session, mob)) {
                continue;
            }
            FollowGoal goal = new FollowGoal(mob, session);
            goal.setTarget(session.landing.x, session.landing.z);
            if (goal.repath()) {
                mob.goalSelector.addGoal(-1, goal);
                session.followers.add(goal);
            }
        }
    }

    private boolean eligible(Mob mob, ServerLevel level) {
        if (!mob.isAlive() || mob.isRemoved() || mob.isPassenger() || mob.level() != level) {
            return false;
        }
        String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
        boolean hostile = mob.getType().getCategory() == MobCategory.MONSTER;
        return !config.excludes(entityId, hostile);
    }

    private boolean hasFollower(Session session, Mob mob) {
        for (FollowGoal goal : session.followers) {
            if (goal.mob == mob) {
                return true;
            }
        }
        return false;
    }

    private void pruneFollowers(Session session) {
        double radiusSquared = config.attractionRadius() * config.attractionRadius();
        Iterator<FollowGoal> iterator = session.followers.iterator();
        while (iterator.hasNext()) {
            FollowGoal goal = iterator.next();
            Mob mob = goal.mob;
            double dx = mob.getX() - session.landing.x;
            double dz = mob.getZ() - session.landing.z;
            if (!eligible(mob, session.level) || dx * dx + dz * dz > radiusSquared
                    || Math.abs(mob.getY() - session.landing.y) > config.verticalRange()) {
                mob.goalSelector.removeGoal(goal);
                iterator.remove();
            } else {
                goal.setTarget(session.landing.x, session.landing.z);
            }
        }
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
            if (!goal.mob.isAlive() || goal.mob.isRemoved() || goal.mob.level() != session.level
                    || goal.unreachable) {
                goal.mob.goalSelector.removeGoal(goal);
                iterator.remove();
            }
        }
    }

    private void clear(Session session) {
        if (session.mountedEntity != null && session.player.getVehicle() == session.mountedEntity) {
            boolean landed = session.player.onGround() || session.mountedEntity.onGround();
            session.player.stopRiding();
            session.player.resetFallDistance();
            if (landed) {
                LOGGER.info("Player {} safely dismounted", session.player.getGameProfile().getName());
            }
        }
        clearGoals(session);
    }

    private void clearGoals(Session session) {
        for (FollowGoal goal : session.followers) {
            goal.mob.goalSelector.removeGoal(goal);
        }
        session.followers.clear();
    }

    private static final class MotionSample {
        private final Vec3 position;
        private final long lastSeenTick;
        private long lastPredictionTick;

        private MotionSample(Vec3 position, long lastSeenTick, long lastPredictionTick) {
            this.position = position;
            this.lastSeenTick = lastSeenTick;
            this.lastPredictionTick = lastPredictionTick;
        }
    }

    private static final class Session {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final List<FollowGoal> followers = new ArrayList<>();
        private FallTrajectory.Result landing;
        private Mob mountedEntity;
        private long lastPredictionTick;
        private long lastSeenTick;
        private boolean fallDamageHandled;

        private Session(ServerPlayer player, ServerLevel level, FallTrajectory.Result landing) {
            this.player = player;
            this.level = level;
            this.landing = landing;
        }
    }

    private static final class FollowGoal extends Goal {
        private final Mob mob;
        private final Session session;
        private double targetX;
        private double targetZ;
        private double lastPathX = Double.NaN;
        private double lastPathZ = Double.NaN;
        private int repathDelay;
        private boolean unreachable;

        private FollowGoal(Mob mob, Session session) {
            this.mob = mob;
            this.session = session;
            setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !unreachable && session.player.isAlive() && session.mountedEntity == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse() && mob.isAlive() && !mob.isRemoved();
        }

        @Override
        public void tick() {
            if (repathDelay-- <= 0) {
                repathDelay = GOAL_REPATH_INTERVAL;
                double deltaX = targetX - lastPathX;
                double deltaZ = targetZ - lastPathZ;
                if (Double.isNaN(lastPathX) || deltaX * deltaX + deltaZ * deltaZ >= 4
                        || (mob.getNavigation().isDone() && !atTarget())) {
                    repath();
                }
            }
        }

        @Override
        public void stop() {
            mob.getNavigation().stop();
        }

        private void setTarget(double x, double z) {
            targetX = x;
            targetZ = z;
        }

        private boolean repath() {
            lastPathX = targetX;
            lastPathZ = targetZ;
            if (atTarget()) {
                mob.getNavigation().stop();
                return true;
            }
            net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(
                    net.minecraft.core.BlockPos.containing(targetX, mob.getY(), targetZ), 0);
            unreachable = !mob.getNavigation().moveTo(path, FOLLOW_SPEED);
            return !unreachable;
        }

        private boolean atTarget() {
            double contactDistance = (mob.getBbWidth() + session.player.getBbWidth()) / 2;
            return Math.abs(mob.getX() - targetX) < contactDistance
                    && Math.abs(mob.getZ() - targetZ) < contactDistance;
        }
    }
}
