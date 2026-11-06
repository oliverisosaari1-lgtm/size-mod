package com.iikka.morph;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** What V does for each mob. Returns the cooldown in ticks. */
public class Powers {
    private static EntityType<?> type(String id) {
        return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace(id));
    }

    private static void sound(ServerPlayer p, Object event, float pitch) {
        SoundEvent s = event instanceof Holder<?> h ? (SoundEvent) h.value() : (SoundEvent) event;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), s, SoundSource.PLAYERS, 1f, pitch);
    }

    private static void particles(ServerPlayer p, ParticleOptions type, Vec3 at, int count, double spread, double speed) {
        ((ServerLevel) p.level()).sendParticles(type, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    private static void effect(LivingEntity e, Holder<MobEffect> eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp));
    }

    private static BlockHitResult lookBlock(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        return p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
    }

    /** The mob or player you're looking at. */
    private static LivingEntity lookEntity(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity e : p.level().getEntities(p, new AABB(eye, end).inflate(1.5))) {
            if (!(e instanceof LivingEntity le) || e.getTags().contains("morph_disguise")) continue;
            var hit = e.getBoundingBox().inflate(0.4).clip(eye, end);
            if (hit.isPresent() && eye.distanceTo(hit.get()) < bestDist) { bestDist = eye.distanceTo(hit.get()); best = le; }
        }
        return best;
    }

    private static void near(ServerPlayer p, double r, java.util.function.Consumer<LivingEntity> what) {
        for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(r)))
            if (e instanceof LivingEntity le && !e.getTags().contains("morph_disguise")) what.accept(le);
    }

    private static void shoot(ServerPlayer p, String id, double speed) {
        Entity e = type(id).create(p.level(), EntitySpawnReason.TRIGGERED);
        if (e == null) return;
        Vec3 look = p.getLookAngle();
        Vec3 at = p.getEyePosition().add(look.scale(1.5));
        e.setPos(at.x, at.y, at.z);
        e.setDeltaMovement(look.scale(speed));
        if (e instanceof net.minecraft.world.entity.TraceableEntity) {
            try { e.getClass().getMethod("setOwner", Entity.class).invoke(e, p); } catch (Exception ignored) { }
        }
        p.level().addFreshEntity(e);
    }

    private static void push(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.hurtMarked = true;
    }

    public static int use(ServerPlayer p, String fullId) {
        String id = fullId.replace("minecraft:", "");
        ServerLevel level = (ServerLevel) p.level();
        Vec3 look = p.getLookAngle();
        switch (id) {
            case "enderman" -> {
                BlockHitResult hit = lookBlock(p, 48);
                Vec3 to = hit.getType() == HitResult.Type.BLOCK ? Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection())) : p.position().add(look.scale(16));
                particles(p, ParticleTypes.PORTAL, p.position().add(0, 1, 0), 60, 0.5, 0.5);
                p.teleportTo(to.x, to.y, to.z);
                p.fallDistance = 0;
                particles(p, ParticleTypes.PORTAL, to.add(0, 1, 0), 60, 0.5, 0.5);
                sound(p, SoundEvents.ENDERMAN_TELEPORT, 1f);
                return 10;
            }
            case "creeper" -> {
                p.setInvulnerable(true);
                level.explode(p, p.getX(), p.getY(), p.getZ(), 3.5f, Level.ExplosionInteraction.MOB);
                p.setInvulnerable(false);
                return 40;
            }
            case "skeleton", "stray", "bogged", "pillager", "piglin" -> {
                for (int i = 0; i < 3; i++) shoot(p, "arrow", 3.0);
                sound(p, SoundEvents.ARROW_SHOOT, 1f);
                return 8;
            }
            case "blaze" -> { for (int i = 0; i < 3; i++) shoot(p, "small_fireball", 1.5); sound(p, SoundEvents.BLAZE_SHOOT, 1f); return 15; }
            case "ghast", "happy_ghast" -> { shoot(p, "fireball", 1.5); sound(p, SoundEvents.GHAST_SHOOT, 1f); return 30; }
            case "wither_skeleton" -> { shoot(p, "wither_skull", 1.5); sound(p, SoundEvents.WITHER_SHOOT, 1f); return 20; }
            case "snow_golem" -> { for (int i = 0; i < 5; i++) shoot(p, "snowball", 2.0); sound(p, SoundEvents.SNOW_GOLEM_SHOOT, 1f); return 5; }
            case "llama", "trader_llama" -> { shoot(p, "llama_spit", 2.0); sound(p, SoundEvents.LLAMA_SPIT, 1f); return 10; }
            case "breeze" -> { shoot(p, "breeze_wind_charge", 1.5); sound(p, SoundEvents.BREEZE_SHOOT, 1f); return 10; }
            case "witch" -> { effect(p, MobEffects.REGENERATION, 200, 1); effect(p, MobEffects.SPEED, 200, 1); sound(p, SoundEvents.WITCH_DRINK, 1f); return 100; }
            case "spider", "cave_spider" -> {
                BlockHitResult hit = lookBlock(p, 32);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    BlockPos b = hit.getBlockPos().relative(hit.getDirection());
                    if (level.getBlockState(b).isAir()) level.setBlockAndUpdate(b, Blocks.COBWEB.defaultBlockState());
                }
                push(p, look.scale(1.2).add(0, 0.5, 0));
                sound(p, SoundEvents.SPIDER_AMBIENT, 1f);
                return 20;
            }
            case "zombie", "husk", "drowned", "zombie_villager", "zombified_piglin", "piglin_brute", "vindicator" -> {
                effect(p, MobEffects.STRENGTH, 200, 1); effect(p, MobEffects.SPEED, 200, 0);
                sound(p, SoundEvents.ZOMBIE_AMBIENT, 0.8f);
                return 100;
            }
            case "iron_golem", "ravager", "hoglin", "zoglin" -> {
                near(p, 5, e -> { e.hurtServer(level, p.damageSources().playerAttack(p), 10); e.setDeltaMovement(e.getDeltaMovement().add(0, 1.0, 0)); e.hurtMarked = true; });
                particles(p, ParticleTypes.EXPLOSION, p.position(), 10, 2, 0);
                sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8f);
                return 40;
            }
            case "warden" -> {
                Vec3 eye = p.getEyePosition();
                for (int i = 1; i < 20; i++) particles(p, ParticleTypes.SONIC_BOOM, eye.add(look.scale(i)), 1, 0, 0);
                LivingEntity t = lookEntity(p, 20);
                if (t != null) t.hurtServer(level, level.damageSources().sonicBoom(p), 15);
                sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1f);
                return 60;
            }
            case "evoker", "illusioner" -> {
                for (int i = 1; i <= 10; i++) {
                    Vec3 at = p.position().add(look.x * i * 1.3, 0, look.z * i * 1.3);
                    Entity f = type("evoker_fangs").create(level, EntitySpawnReason.TRIGGERED);
                    if (f != null) { f.setPos(at.x, Math.floor(p.getY()), at.z); level.addFreshEntity(f); }
                }
                sound(p, SoundEvents.EVOKER_CAST_SPELL, 1f);
                return 40;
            }
            case "shulker" -> {
                LivingEntity t = lookEntity(p, 24);
                if (t != null) effect(t, MobEffects.LEVITATION, 100, 1);
                sound(p, SoundEvents.SHULKER_SHOOT, 1f);
                return 30;
            }
            case "squid", "glow_squid" -> {
                near(p, 6, e -> effect(e, MobEffects.BLINDNESS, 100, 0));
                particles(p, id.equals("glow_squid") ? ParticleTypes.GLOW_SQUID_INK : ParticleTypes.SQUID_INK, p.position().add(0, 1, 0), 80, 1.5, 0.1);
                sound(p, SoundEvents.SQUID_SQUIRT, 1f);
                return 40;
            }
            case "pufferfish", "bee" -> {
                near(p, 4, e -> effect(e, MobEffects.POISON, 100, 1));
                sound(p, SoundEvents.PUFFER_FISH_BLOW_UP, 1f);
                return 40;
            }
            case "chicken", "parrot", "bat", "phantom", "allay", "vex", "ender_dragon" -> {
                push(p, look.scale(1.5).add(0, 1.0, 0));
                effect(p, MobEffects.SLOW_FALLING, 100, 0);
                sound(p, SoundEvents.PHANTOM_FLAP, 1f);
                return 10;
            }
            case "rabbit", "horse", "donkey", "mule", "goat", "frog", "camel", "slime", "magma_cube", "skeleton_horse", "zombie_horse" -> {
                push(p, look.scale(0.8).add(0, 1.4, 0));
                effect(p, MobEffects.SLOW_FALLING, 40, 0);
                sound(p, SoundEvents.SLIME_JUMP, 1f);
                return 15;
            }
            case "wolf", "fox", "cat", "ocelot", "cheetah", "panda", "polar_bear", "armadillo" -> {
                push(p, look.scale(1.8).add(0, 0.4, 0));
                effect(p, MobEffects.SPEED, 100, 2);
                sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f);
                return 30;
            }
            case "dolphin", "axolotl", "turtle", "cod", "salmon", "tropical_fish", "tadpole", "guardian", "elder_guardian", "nautilus" -> {
                effect(p, MobEffects.DOLPHINS_GRACE, 400, 0); effect(p, MobEffects.WATER_BREATHING, 1200, 0);
                if (id.contains("guardian")) { LivingEntity t = lookEntity(p, 16); if (t != null) t.hurtServer(level, p.damageSources().magic(), 8); }
                sound(p, SoundEvents.DOLPHIN_PLAY, 1f);
                return 40;
            }
            case "strider" -> { effect(p, MobEffects.FIRE_RESISTANCE, 1200, 0); sound(p, SoundEvents.STRIDER_HAPPY, 1f); return 100; }
            case "villager", "wandering_trader" -> { effect(p, MobEffects.HERO_OF_THE_VILLAGE, 1200, 0); sound(p, SoundEvents.VILLAGER_YES, 1f); return 100; }
            case "sheep", "cow", "mooshroom", "pig", "sniffer", "wither" -> { p.getFoodData().eat(6, 0.6f); p.heal(4); sound(p, SoundEvents.GENERIC_EAT, 1f); return 60; }
            default -> {
                effect(p, MobEffects.SPEED, 100, 1); effect(p, MobEffects.JUMP_BOOST, 100, 1);
                sound(p, SoundEvents.PLAYER_LEVELUP, 1.5f);
                return 60;
            }
        }
    }
}
