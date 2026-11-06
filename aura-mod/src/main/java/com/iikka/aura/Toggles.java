package com.iikka.aura;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/** On/off powers: /lightning /magnet /laser /slime /swords, plus the one-shot /volcano. */
public class Toggles {
    static final Map<String, Set<UUID>> on = new HashMap<>();

    static boolean toggle(String what, ServerPlayer p) {
        Set<UUID> s = on.computeIfAbsent(what, k -> new HashSet<>());
        if (s.remove(p.getUUID())) { if (what.equals("swords")) removeSwords(p.getUUID()); return false; }
        s.add(p.getUUID());
        return true;
    }

    static boolean has(String what, ServerPlayer p) { return on.getOrDefault(what, Set.of()).contains(p.getUUID()); }

    static void leave(UUID id) { on.values().forEach(s -> s.remove(id)); removeSwords(id); }

    private static BlockHitResult look(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        return p.level().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
    }

    // ---- swords ----
    private static final Map<UUID, List<Display.ItemDisplay>> swords = new HashMap<>();
    private static void removeSwords(UUID id) { var l = swords.remove(id); if (l != null) l.forEach(Entity::discard); }

    static boolean ours(Entity e) { for (var l : swords.values()) if (l.contains(e)) return true; return false; }

    // ---- volcano ----
    private static class Volcano { ServerLevel level; BlockPos base; int age; }
    private static class Lava { Display.BlockDisplay d; Vec3 vel; int life = 100; }
    private static final List<Volcano> volcanoes = new ArrayList<>();
    private static final List<Lava> lava = new ArrayList<>();
    private static final int H = 12;

    static void volcano(ServerPlayer p) {
        BlockHitResult hit = look(p, 40);
        BlockPos at = hit.getType() == HitResult.Type.MISS ? BlockPos.containing(p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(15))) : hit.getBlockPos();
        Volcano v = new Volcano();
        v.level = (ServerLevel) p.level();
        v.base = at;
        volcanoes.add(v);
    }

    private static void tickVolcanoes() {
        for (Iterator<Volcano> it = volcanoes.iterator(); it.hasNext(); ) {
            Volcano v = it.next();
            v.age++;
            ServerLevel level = v.level;
            if (v.age <= H * 5) {
                // Rise up one layer every 5 ticks
                if (v.age % 5 == 0) {
                    int y = v.age / 5;
                    double r = (H - y) * 0.75 + 1.5;
                    for (int x = (int) -r; x <= r; x++) for (int z = (int) -r; z <= r; z++) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d > r) continue;
                        BlockPos pos = v.base.offset(x, y, z);
                        boolean crater = y >= H - 2 && d < 1.6;
                        level.setBlock(pos, crater ? Blocks.AIR.defaultBlockState() : (level.random.nextInt(4) == 0 ? Blocks.MAGMA_BLOCK : (d > r - 1 ? Blocks.BASALT : Blocks.BLACKSTONE)).defaultBlockState(), 3);
                    }
                    level.playSound(null, v.base.getX(), v.base.getY() + y, v.base.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.6f, 0.5f);
                    level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, v.base.getX(), v.base.getY() + y, v.base.getZ(), 30, 4, 1, 4, 0.02);
                }
                if (v.age == H * 5) level.setBlock(v.base.above(H - 2), Blocks.LAVA.defaultBlockState(), 3);
                continue;
            }
            if (v.age > H * 5 + 300) { it.remove(); continue; }
            // Erupt: lava chunks fly out of the top
            Vec3 top = Vec3.atCenterOf(v.base.above(H));
            level.sendParticles(ParticleTypes.LAVA, top.x, top.y, top.z, 6, 0.6, 0.3, 0.6, 0.1);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, top.x, top.y + 1, top.z, 8, 0.8, 0.5, 0.8, 0.05);
            if (v.age % 3 == 0) {
                Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level, EntitySpawnReason.COMMAND);
                if (d != null) {
                    d.setBlockState(Blocks.MAGMA_BLOCK.defaultBlockState());
                    d.setPosRotInterpolationDuration(1);
                    d.addTag(Powers.TAG);
                    d.setPos(top.x - 0.5, top.y, top.z - 0.5);
                    level.addFreshEntity(d);
                    Lava l = new Lava();
                    l.d = d;
                    l.vel = new Vec3(level.random.nextGaussian() * 0.35, 1.0 + level.random.nextDouble() * 0.6, level.random.nextGaussian() * 0.35);
                    lava.add(l);
                }
            }
        }
        for (Iterator<Lava> it = lava.iterator(); it.hasNext(); ) {
            Lava l = it.next();
            ServerLevel level = (ServerLevel) l.d.level();
            l.vel = l.vel.add(0, -0.06, 0);
            Vec3 next = l.d.position().add(l.vel);
            level.sendParticles(ParticleTypes.FLAME, next.x + 0.5, next.y + 0.5, next.z + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
            BlockPos pos = BlockPos.containing(next.add(0.5, 0, 0.5));
            boolean hit = l.vel.y < 0 && !level.getBlockState(pos).isAir();
            if (hit || --l.life <= 0 || l.d.isRemoved()) {
                if (hit && level.getBlockState(pos.above()).isAir()) level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
                if (hit) level.sendParticles(ParticleTypes.LAVA, next.x, next.y + 1, next.z, 10, 0.5, 0.3, 0.5, 0.1);
                l.d.discard();
                it.remove();
                continue;
            }
            l.d.teleportTo(next.x, next.y, next.z);
        }
    }

    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) p.level();
            if (has("lightning", p) && t % 10 == 0) {
                BlockHitResult hit = look(p, 120);
                if (hit.getType() != HitResult.Type.MISS) {
                    Entity bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
                    if (bolt != null) { Vec3 at = hit.getLocation(); bolt.setPos(at.x, at.y, at.z); level.addFreshEntity(bolt); }
                }
            }
            if (has("magnet", p)) {
                for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(24), e -> e instanceof ItemEntity || e instanceof Mob)) {
                    Vec3 pull = p.position().add(0, 0.5, 0).subtract(e.position());
                    if (pull.lengthSqr() < 2) continue;
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.normalize().scale(0.5)));
                    e.hurtMarked = true;
                }
                if (t % 4 == 0) level.sendParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 20, 3, 1, 3, 0.5);
            }
            if (has("laser", p)) {
                Vec3 eye = p.getEyePosition(), dir = p.getLookAngle();
                BlockHitResult hit = look(p, 60);
                double len = hit.getType() == HitResult.Type.MISS ? 60 : hit.getLocation().distanceTo(eye);
                for (double d = 1; d < len; d += 0.5) {
                    Vec3 at = eye.add(dir.scale(d));
                    level.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
                for (Entity e : level.getEntities(p, new AABB(eye, eye.add(dir.scale(len))).inflate(1))) {
                    if (e instanceof LivingEntity le && e.getBoundingBox().inflate(0.4).clip(eye, eye.add(dir.scale(len))).isPresent()) {
                        le.igniteForSeconds(4);
                        if (t % 5 == 0) le.hurtServer(level, p.damageSources().playerAttack(p), 4);
                    }
                }
                // Burn through the blocks it hits
                if (hit.getType() != HitResult.Type.MISS && t % 2 == 0) {
                    BlockPos pos = hit.getBlockPos();
                    BlockState s = level.getBlockState(pos);
                    if (s.getDestroySpeed(level, pos) >= 0) {
                        level.destroyBlock(pos, false, p);
                        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
                    }
                }
            }
            if (has("slime", p)) {
                // Blocks you stand on or walk into become slime blocks
                BlockPos feet = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
                List<BlockPos> touch = new ArrayList<>(List.of(feet));
                BlockPos body = BlockPos.containing(p.position());
                for (Direction d : Direction.Plane.HORIZONTAL) if (p.horizontalCollision) { touch.add(body.relative(d)); touch.add(body.relative(d).above()); }
                for (BlockPos pos : touch) {
                    BlockState s = level.getBlockState(pos);
                    if (s.isAir() || s.is(Blocks.SLIME_BLOCK) || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, pos) < 0 || s.hasBlockEntity()) continue;
                    if (!s.isCollisionShapeFullBlock(level, pos)) continue;
                    level.setBlock(pos, Blocks.SLIME_BLOCK.defaultBlockState(), 3);
                }
            }
            if (has("swords", p)) {
                List<Display.ItemDisplay> list = swords.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
                if (list.isEmpty() || list.get(0).isRemoved() || list.get(0).level() != level) {
                    list.forEach(Entity::discard);
                    list.clear();
                    for (int i = 0; i < 6; i++) {
                        Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.COMMAND);
                        if (d == null) continue;
                        d.setItemStack(new ItemStack(Items.DIAMOND_SWORD));
                        d.setPosRotInterpolationDuration(1);
                        d.addTag(Powers.TAG);
                        d.setPos(p.getX(), p.getY() + 1, p.getZ());
                        level.addFreshEntity(d);
                        list.add(d);
                    }
                }
                for (int i = 0; i < list.size(); i++) {
                    double a = t * 0.25 + i * Math.PI * 2 / list.size();
                    Vec3 at = p.position().add(Math.cos(a) * 2.5, 1.1 + Math.sin(t * 0.1 + i) * 0.3, Math.sin(a) * 2.5);
                    Display.ItemDisplay d = list.get(i);
                    d.teleportTo(at.x, at.y, at.z);
                    d.setYRot((float) Math.toDegrees(-a));
                    if (t % 10 == 0) for (Entity e : level.getEntities(p, new AABB(at, at).inflate(1.2), e -> e instanceof Mob && !e.getTags().contains(Powers.TAG))) {
                        ((LivingEntity) e).hurtServer(level, p.damageSources().playerAttack(p), 6);
                        level.sendParticles(ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + 1, e.getZ(), 1, 0, 0, 0, 0);
                    }
                }
            }
        }
        tickVolcanoes();
    }

    static void clear() {
        swords.values().forEach(l -> l.forEach(Entity::discard)); swords.clear();
        lava.forEach(l -> l.d.discard()); lava.clear();
        volcanoes.clear();
        on.clear();
    }
}
