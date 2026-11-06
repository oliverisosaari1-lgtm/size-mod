package com.iikka.aura;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** /blackhole, /meteor and /dragonride. */
public class Powers {
    static final String TAG = "aura_fx";
    private static final List<Runnable> none = List.of();

    // ---------- Black hole ----------
    private static class Hole { ServerLevel level; Vec3 at; UUID owner; int life = 300; final List<Display.BlockDisplay> ball = new ArrayList<>(); }
    private static class Bit { Display.BlockDisplay d; Vec3 to; int life = 40; }
    private static final List<Hole> holes = new ArrayList<>();
    private static final List<Bit> bits = new ArrayList<>();

    private static Display.BlockDisplay display(ServerLevel level, BlockState state, Vec3 corner, int interp) {
        Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
        if (d == null) return null;
        d.setBlockState(state);
        d.setPosRotInterpolationDuration(interp);
        d.addTag(TAG);
        d.setPos(corner.x, corner.y, corner.z);
        level.addFreshEntity(d);
        return d;
    }

    static void blackhole(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        HitResult hit = p.pick(20, 0, false);
        Vec3 at = hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(12)) : hit.getLocation().add(0, 2, 0);
        Hole h = new Hole();
        h.level = level; h.at = at; h.owner = p.getUUID();
        for (int x = -2; x <= 2; x++) for (int y = -2; y <= 2; y++) for (int z = -2; z <= 2; z++)
            if (x * x + y * y + z * z <= 5) {
                var d = display(level, Blocks.BLACK_CONCRETE.defaultBlockState(), at.add(x - 0.5, y - 0.5, z - 0.5), 1);
                if (d != null) h.ball.add(d);
            }
        holes.add(h);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 2f, 0.5f);
    }

    private static void tickHoles(int t) {
        for (Iterator<Hole> it = holes.iterator(); it.hasNext(); ) {
            Hole h = it.next();
            if (--h.life <= 0) {
                h.ball.forEach(Entity::discard);
                h.level.explode(null, h.at.x, h.at.y, h.at.z, 2f, Level.ExplosionInteraction.NONE);
                it.remove();
                continue;
            }
            h.level.sendParticles(ParticleTypes.REVERSE_PORTAL, h.at.x, h.at.y, h.at.z, 20, 3, 3, 3, 0.1);
            // Suck in mobs and other players (not you)
            for (Entity e : h.level.getEntities((Entity) null, new AABB(h.at, h.at).inflate(18), e -> !e.getTags().contains(TAG) && !e.getUUID().equals(h.owner) && !(e instanceof Display))) {
                Vec3 pull = h.at.subtract(e.position()).normalize().scale(0.35);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.7).add(pull));
                e.hurtMarked = true;
                if (e instanceof LivingEntity le && e.distanceToSqr(h.at) < 6 && t % 10 == 0) le.hurtServer(h.level, h.level.damageSources().magic(), 4);
                if (!(e instanceof LivingEntity) && e.distanceToSqr(h.at) < 4) e.discard(); // items etc. vanish into it
            }
            // Rip blocks out of the ground and pull them in
            for (int i = 0; i < 4; i++) {
                BlockPos pos = BlockPos.containing(h.at.add(h.level.random.nextGaussian() * 5, h.level.random.nextGaussian() * 5, h.level.random.nextGaussian() * 5));
                BlockState s = h.level.getBlockState(pos);
                if (s.isAir() || s.getDestroySpeed(h.level, pos) < 0 || !s.getFluidState().isEmpty()) continue;
                h.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                Bit b = new Bit();
                b.d = display(h.level, s, Vec3.atLowerCornerOf(pos), 2);
                b.to = h.at.subtract(0.5, 0.5, 0.5);
                if (b.d != null) bits.add(b);
            }
        }
        for (Iterator<Bit> it = bits.iterator(); it.hasNext(); ) {
            Bit b = it.next();
            Vec3 now = b.d.position();
            Vec3 step = b.to.subtract(now);
            if (--b.life <= 0 || step.lengthSqr() < 1 || b.d.isRemoved()) { b.d.discard(); it.remove(); continue; }
            Vec3 next = now.add(step.normalize().scale(Math.min(step.length(), 0.8)));
            b.d.teleportTo(next.x, next.y, next.z);
        }
    }

    // ---------- Meteors ----------
    private static class Rain { ServerLevel level; UUID owner; int life = 200; }
    private static class Rock { Display.BlockDisplay d; Vec3 vel; int life = 120; }
    private static final List<Rain> rains = new ArrayList<>();
    private static final List<Rock> rocks = new ArrayList<>();

    static void meteor(ServerPlayer p) {
        Rain r = new Rain(); r.level = (ServerLevel) p.level(); r.owner = p.getUUID();
        rains.add(r);
    }

    private static void tickMeteors(MinecraftServer server, int t) {
        for (Iterator<Rain> it = rains.iterator(); it.hasNext(); ) {
            Rain r = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(r.owner);
            if (p == null || --r.life <= 0) { it.remove(); continue; }
            if (t % 4 != 0) continue;
            double a = r.level.random.nextDouble() * Math.PI * 2, dist = 10 + r.level.random.nextDouble() * 25;
            Vec3 start = p.position().add(Math.cos(a) * dist + 15, 45, Math.sin(a) * dist + 15);
            Rock k = new Rock();
            k.d = display(r.level, Blocks.MAGMA_BLOCK.defaultBlockState(), start, 1);
            k.vel = new Vec3(-0.5, -1.4, -0.5);
            if (k.d != null) { k.d.setRemainingFireTicks(1000); rocks.add(k); }
        }
        for (Iterator<Rock> it = rocks.iterator(); it.hasNext(); ) {
            Rock k = it.next();
            ServerLevel level = (ServerLevel) k.d.level();
            Vec3 next = k.d.position().add(k.vel);
            level.sendParticles(ParticleTypes.FLAME, next.x + 0.5, next.y + 0.5, next.z + 0.5, 6, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, next.x + 0.5, next.y + 1, next.z + 0.5, 2, 0.2, 0.2, 0.2, 0.01);
            BlockPos hitPos = BlockPos.containing(next.add(0.5, 0, 0.5));
            boolean hit = !level.getBlockState(hitPos).isAir() || !level.getFluidState(hitPos).isEmpty() || next.y < level.getMinY();
            if (hit || --k.life <= 0 || k.d.isRemoved()) {
                if (hit) level.explode(null, next.x + 0.5, next.y + 0.5, next.z + 0.5, 3f, true, Level.ExplosionInteraction.TNT);
                k.d.discard();
                it.remove();
                continue;
            }
            k.d.teleportTo(next.x, next.y, next.z);
        }
    }

    // ---------- Dragon ride ----------
    private static final Map<UUID, Mob> rides = new HashMap<>();

    static String dragonride(ServerPlayer p) {
        Mob old = rides.remove(p.getUUID());
        if (old != null) { stopRide(p, old); return "You got off."; }
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(8));
        Mob best = null;
        double bd = 99;
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(8), e -> e instanceof Mob)) {
            if (e.getTags().contains(TAG)) continue;
            Optional<Vec3> on = e.getBoundingBox().inflate(0.5).clip(eye, end);
            double d = e.distanceTo(p);
            if (on.isPresent() && d < bd) { bd = d; best = (Mob) e; }
        }
        if (best == null) return null;
        best.ejectPassengers();
        p.startRiding(best, true, true);
        best.setNoGravity(true);
        best.setTarget(null);
        rides.put(p.getUUID(), best);
        return "You're riding it! Look where you want to fly, hold W to go. Sneak to get off.";
    }

    private static void stopRide(ServerPlayer p, Mob m) {
        m.setNoGravity(false);
        if (p.getVehicle() == m) p.stopRiding();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
    }

    private static void tickRides(MinecraftServer server) {
        for (Iterator<Map.Entry<UUID, Mob>> it = rides.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            Mob m = en.getValue();
            if (p == null || !m.isAlive() || p.getVehicle() != m) {
                m.setNoGravity(false);
                if (p != null) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
                it.remove();
                continue;
            }
            p.fallDistance = 0;
            m.fallDistance = 0;
            m.setTarget(null);
            m.getNavigation().stop();
            m.setYRot(p.getYRot()); m.setYBodyRot(p.getYRot()); m.setYHeadRot(p.getYRot()); m.setXRot(p.getXRot());
            boolean forward = p.getLastClientInput().forward();
            Vec3 v = forward ? p.getLookAngle().scale(0.9) : Vec3.ZERO;
            m.setDeltaMovement(Vec3.ZERO);
            m.move(MoverType.SELF, v);
        }
    }

    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        tickHoles(t);
        tickMeteors(server, t);
        tickRides(server);
    }

    static void clear() {
        holes.forEach(h -> h.ball.forEach(Entity::discard)); holes.clear();
        bits.forEach(b -> b.d.discard()); bits.clear();
        rocks.forEach(k -> k.d.discard()); rocks.clear();
        rains.clear();
        rides.values().forEach(m -> m.setNoGravity(false)); rides.clear();
    }
}
