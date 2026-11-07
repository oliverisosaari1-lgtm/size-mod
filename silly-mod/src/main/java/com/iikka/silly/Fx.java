package com.iikka.silly;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** Small helpers the weapons use: sounds, particles, knockback, aiming, and things you can throw. */
public final class Fx {
    private Fx() { }

    public static final Random R = new Random();

    // ---------- sound and particles ----------

    public static void sound(ServerLevel l, Vec3 p, String id, float vol, float pitch) {
        try {
            SoundEvent s = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(id));
            if (s != null) l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
        } catch (Throwable ignored) { }
    }

    public static void parts(ServerLevel l, ParticleOptions o, Vec3 p, int n, double spread, double speed) {
        l.sendParticles(o, p.x, p.y, p.z, n, spread, spread, spread, speed);
    }

    /** A line of particles from a to b. */
    public static void beam(ServerLevel l, ParticleOptions o, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.01) return;
        Vec3 u = d.scale(1 / len);
        for (double t = 0; t < len; t += step) {
            Vec3 q = a.add(u.scale(t));
            l.sendParticles(o, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    // ---------- who is where ----------

    public static List<LivingEntity> around(ServerLevel l, Vec3 c, double r, Entity except) {
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, x -> x.isAlive() && x != except && !Flying.isThrown(x))) {
            if (e.position().distanceToSqr(c) <= r * r) out.add(e);
        }
        return out;
    }

    /** Everyone inside a cone in front of the player. */
    public static void cone(ServerPlayer p, double range, double minDot, Consumer<LivingEntity> act) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (LivingEntity e : around((ServerLevel) p.level(), eye, range, p)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            double len = to.length();
            if (len < 0.001 || to.scale(1 / len).dot(look) >= minDot) act.accept(e);
        }
    }

    /** The point the player is looking at (a block or the end of the range). */
    public static Vec3 lookPoint(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), end = eye.add(p.getLookAngle().scale(range));
        return p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p)).getLocation();
    }

    /** The creature the player is looking at, or null. */
    public static LivingEntity lookTarget(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        double wall = lookPoint(p, range).distanceTo(eye) + 0.5;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : around((ServerLevel) p.level(), eye, range, p)) {
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            Vec3 to = c.subtract(eye);
            double along = to.dot(look);
            if (along <= 0 || along > wall) continue;
            double off = to.subtract(look.scale(along)).length();
            if (off < Math.max(0.9, e.getBbWidth()) && along < bestD) { best = e; bestD = along; }
        }
        return best;
    }

    // ---------- hurting and pushing ----------

    public static void hurt(LivingEntity e, ServerPlayer by, float dmg) {
        if (dmg <= 0) return;
        ServerLevel l = (ServerLevel) e.level();
        e.hurtServer(l, by != null ? l.damageSources().playerAttack(by) : l.damageSources().magic(), dmg);
    }

    /** Push e away from a point. */
    public static void knock(Entity e, Vec3 from, double power, double up) {
        Vec3 d = e.position().subtract(from);
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 0.0001) d = new Vec3(R.nextDouble() - 0.5, 0, R.nextDouble() - 0.5);
        d = d.normalize().scale(power);
        push(e, new Vec3(d.x, up, d.z));
    }

    public static void push(Entity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
        if (e instanceof ServerPlayer sp) SillyMod.protectFall(sp);
    }

    public static void setVel(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        if (e instanceof ServerPlayer sp) SillyMod.protectFall(sp);
    }

    public static void eff(LivingEntity e, Holder<MobEffect> m, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(m, ticks, amp));
    }

    public static void tp(Entity e, Vec3 to) {
        e.teleportTo(to.x, to.y, to.z);
        if (e instanceof ServerPlayer sp) SillyMod.protectFall(sp);
    }

    /** Places a block only where there is air (so arenas don't get wrecked). */
    public static void placeInAir(ServerLevel l, BlockPos pos, BlockState s) {
        if (l.getBlockState(pos).isAir()) l.setBlock(pos, s, 3);
    }

    /** Lightning that looks real but doesn't burn the map down. */
    public static void lightning(ServerLevel l, Vec3 at) { lightning(l, at, null); }

    public static void lightning(ServerLevel l, Vec3 at, ServerPlayer by) {
        Entity b = EntityTypes.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
        if (b instanceof net.minecraft.world.entity.LightningBolt bolt) {
            bolt.setVisualOnly(true);
            bolt.snapTo(at.x, at.y, at.z, 0, 0);
            l.addFreshEntity(bolt);
        }
        for (LivingEntity e : around(l, at, 3.5, by)) { hurt(e, by, 6); e.igniteForSeconds(3); knock(e, at, 0.6, 0.4); }
    }

    public static void boom(ServerLevel l, ServerPlayer by, Vec3 at, float power) {
        l.explode(by, at.x, at.y, at.z, power, Level.ExplosionInteraction.NONE);
    }

    /** Spawns a creature or thing and throws it. It does its Impact when it hits something. */
    public static Entity throwThing(ServerPlayer p, EntityType<?> type, Vec3 at, Vec3 vel, Impact imp, float power, boolean noGravity, ParticleOptions trail) {
        ServerLevel l = (ServerLevel) p.level();
        Entity e = type.create(l, EntitySpawnReason.TRIGGERED);
        if (e == null) return null;
        e.snapTo(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        e.setDeltaMovement(vel);
        e.setInvulnerable(true);
        if (noGravity) e.setNoGravity(true);
        if (e instanceof Mob m) m.setNoAi(true);
        if (e instanceof Projectile pr) pr.setOwner(p);
        l.addFreshEntity(e);
        Flying.add(e, p, imp, power, trail);
        return e;
    }

    public static Vec3 spread(Vec3 v, double amount) {
        return v.add((R.nextDouble() - 0.5) * amount, (R.nextDouble() - 0.5) * amount, (R.nextDouble() - 0.5) * amount);
    }

    public static Vec3 muzzle(ServerPlayer p) {
        return p.getEyePosition().add(p.getLookAngle().scale(1.2)).add(0, -0.3, 0);
    }

    // ---------- what a thrown thing does when it lands ----------

    public enum Impact {
        BOOM, KNOCK, FREEZE, BOUNCE, POISON, FIRE, ZAP, CONFUSE, FLOAT, STINK, PARTY, SPLASH, HONK, SLOW, NONE;

        public void apply(ServerLevel l, ServerPlayer by, Vec3 at, float power) {
            double r = 2.2 + power * 1.3;
            switch (this) {
                case BOOM -> boom(l, by, at, 1.2f + power * 1.6f);
                case KNOCK -> {
                    parts(l, ParticleTypes.EXPLOSION, at, 2, 0.4, 0);
                    sound(l, at, "entity.player.attack.knockback", 1, 0.8f);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 2 + power * 3); knock(e, at, 0.8 + power, 0.5 + power * 0.3); }
                }
                case FREEZE -> {
                    parts(l, ParticleTypes.SNOWFLAKE, at, 40, 1.2, 0.05);
                    sound(l, at, "block.glass.break", 1, 1.4f);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1 + power * 2); eff(e, MobEffects.SLOWNESS, 60 + (int) (power * 40), 3); e.setTicksFrozen(200); }
                }
                case BOUNCE -> {
                    parts(l, ParticleTypes.ITEM_SLIME, at, 30, 1, 0.1);
                    sound(l, at, "block.slime_block.fall", 1.2f, 0.8f);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1 + power); push(e, new Vec3(0, 1.0 + power * 0.5, 0)); }
                }
                case POISON -> {
                    parts(l, ParticleTypes.SNEEZE, at, 40, 1.2, 0.02);
                    sound(l, at, "entity.bee.sting", 1, 1);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1 + power); eff(e, MobEffects.POISON, 60 + (int) (power * 40), 1); }
                }
                case FIRE -> {
                    parts(l, ParticleTypes.FLAME, at, 40, 1, 0.05);
                    sound(l, at, "item.firecharge.use", 1, 1);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 2 + power * 2); e.igniteForSeconds(3 + power * 2); }
                }
                case ZAP -> lightning(l, at, by);
                case CONFUSE -> {
                    parts(l, ParticleTypes.WITCH, at, 40, 1.2, 0.05);
                    sound(l, at, "entity.villager.no", 1.5f, 1);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1 + power); eff(e, MobEffects.NAUSEA, 140, 0); eff(e, MobEffects.BLINDNESS, 40, 0); }
                }
                case FLOAT -> {
                    parts(l, ParticleTypes.END_ROD, at, 30, 1, 0.05);
                    sound(l, at, "entity.shulker.shoot", 1, 1.2f);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1); eff(e, MobEffects.LEVITATION, 30 + (int) (power * 20), 1); }
                }
                case STINK -> {
                    parts(l, ParticleTypes.SNEEZE, at, 60, 1.5, 0.02);
                    sound(l, at, "entity.puffer_fish.blow_out", 1.5f, 0.5f);
                    for (LivingEntity e : around(l, at, r + 1, by)) { hurt(e, by, 1 + power); eff(e, MobEffects.NAUSEA, 160, 0); eff(e, MobEffects.HUNGER, 100, 2); }
                }
                case PARTY -> {
                    parts(l, ParticleTypes.FIREWORK, at, 60, 0.6, 0.25);
                    sound(l, at, "entity.firework_rocket.twinkle", 1.5f, 1);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 2 + power * 2); knock(e, at, 0.5 + power * 0.5, 0.4); }
                }
                case SPLASH -> {
                    parts(l, ParticleTypes.SPLASH, at, 80, 1.2, 0.3);
                    sound(l, at, "entity.generic.splash", 1.5f, 1);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1 + power); knock(e, at, 0.6 + power * 0.4, 0.3); e.clearFire(); }
                }
                case HONK -> {
                    parts(l, ParticleTypes.NOTE, at, 20, 1.2, 1);
                    sound(l, at, "entity.goat.screaming.ambient", 2, 1);
                    for (LivingEntity e : around(l, at, r + 1, by)) { hurt(e, by, 1 + power); knock(e, at, 1.2 + power, 0.6); }
                }
                case SLOW -> {
                    parts(l, ParticleTypes.FALLING_HONEY, at, 40, 1.2, 0.02);
                    sound(l, at, "block.honey_block.slide", 1.5f, 0.8f);
                    for (LivingEntity e : around(l, at, r, by)) { hurt(e, by, 1 + power); eff(e, MobEffects.SLOWNESS, 100, 4); eff(e, MobEffects.WEAKNESS, 100, 1); }
                }
                case NONE -> parts(l, ParticleTypes.POOF, at, 10, 0.4, 0.05);
            }
        }
    }

    // ---------- tracking thrown things ----------

    public static final class Flying {
        private final Entity e;
        private final ServerPlayer owner;
        private final Impact imp;
        private final float power;
        private final ParticleOptions trail;
        private int age;
        private Vec3 last;

        private static final List<Flying> ALL = new ArrayList<>();
        private static final java.util.Set<Entity> THROWN = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

        private Flying(Entity e, ServerPlayer owner, Impact imp, float power, ParticleOptions trail) {
            this.e = e; this.owner = owner; this.imp = imp; this.power = power; this.trail = trail; this.last = e.position();
        }

        static void add(Entity e, ServerPlayer owner, Impact imp, float power, ParticleOptions trail) {
            ALL.add(new Flying(e, owner, imp, power, trail));
            THROWN.add(e);
        }

        public static boolean isThrown(Entity e) { return THROWN.contains(e); }

        public static void tick() {
            Iterator<Flying> it = ALL.iterator();
            while (it.hasNext()) {
                Flying f = it.next();
                try {
                    if (f.step()) { it.remove(); THROWN.remove(f.e); }
                } catch (Throwable t) {
                    it.remove();
                    THROWN.remove(f.e);
                    SillyMod.warn("thrown thing", t);
                }
            }
        }

        /** Returns true when finished. */
        private boolean step() {
            age++;
            if (!(e.level() instanceof ServerLevel l)) return true;
            boolean alive = e.isAlive();
            Vec3 pos = alive ? e.position() : last;
            if (alive && trail != null && age % 2 == 0) l.sendParticles(trail, pos.x, pos.y + e.getBbHeight() / 2, pos.z, 1, 0.05, 0.05, 0.05, 0);
            boolean hit = !alive || age > 100 || (age > 2 && (e.onGround() || e.horizontalCollision || e.verticalCollision || e.isInWater()));
            if (!hit && age > 1) {
                Vec3 c = pos.add(0, e.getBbHeight() / 2, 0);
                for (LivingEntity x : around(l, c, 1.0 + e.getBbWidth() / 2, owner)) { if (x != e) { hit = true; break; } }
            }
            last = pos;
            if (!hit) return false;
            imp.apply(l, owner, pos.add(0, 0.3, 0), power);
            if (e.isAlive()) {
                l.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 0.5, pos.z, 8, 0.3, 0.3, 0.3, 0.02);
                e.discard();
            }
            return true;
        }

        public static void clear() {
            for (Flying f : ALL) if (f.e.isAlive()) f.e.discard();
            ALL.clear();
            THROWN.clear();
        }
    }
}
