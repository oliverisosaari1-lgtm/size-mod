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

    /** Natural traits you always have as this mob (no button needed). Called every second. */
    public static void traits(ServerPlayer p, String fullId) {
        String id = fullId.replace("minecraft:", "");
        switch (id) {
            case "chicken", "parrot", "bat", "phantom", "allay", "vex", "happy_ghast" -> effect(p, MobEffects.SLOW_FALLING, 40, 0);
            case "cod", "salmon", "tropical_fish", "tadpole", "pufferfish", "squid", "glow_squid", "dolphin", "axolotl", "turtle",
                 "drowned", "guardian", "elder_guardian", "nautilus" -> { effect(p, MobEffects.WATER_BREATHING, 40, 0); if (p.isInWater()) effect(p, MobEffects.DOLPHINS_GRACE, 40, 0); }
            case "blaze", "strider", "magma_cube", "ghast", "wither_skeleton", "zombified_piglin", "piglin", "piglin_brute", "hoglin", "zoglin" -> effect(p, MobEffects.FIRE_RESISTANCE, 40, 0);
            case "rabbit", "goat", "frog", "horse", "camel" -> effect(p, MobEffects.JUMP_BOOST, 40, 1);
            case "iron_golem", "ravager", "warden" -> effect(p, MobEffects.RESISTANCE, 40, 0);
            case "wolf", "fox", "ocelot", "cheetah" -> effect(p, MobEffects.SPEED, 40, 0);
            default -> { }
        }
    }

    /** No fall damage as these mobs. */
    public static boolean noFallDamage(String fullId) {
        String id = fullId.replace("minecraft:", "");
        return java.util.Set.of("chicken", "parrot", "bat", "phantom", "allay", "vex", "cat", "ocelot", "happy_ghast", "ghast", "blaze", "breeze", "slime", "magma_cube", "iron_golem").contains(id);
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
            case "skeleton" -> { for (int i = 0; i < 3; i++) shoot(p, "arrow", 3.0); sound(p, SoundEvents.ARROW_SHOOT, 1f); return 8; }
            case "stray" -> { for (int i = 0; i < 3; i++) shoot(p, "arrow", 3.0); LivingEntity t = lookEntity(p, 30); if (t != null) effect(t, MobEffects.SLOWNESS, 100, 2); sound(p, SoundEvents.STRAY_AMBIENT, 1f); return 10; }
            case "bogged" -> { for (int i = 0; i < 3; i++) shoot(p, "arrow", 3.0); LivingEntity t = lookEntity(p, 30); if (t != null) effect(t, MobEffects.POISON, 100, 1); sound(p, SoundEvents.BOGGED_AMBIENT, 1f); return 10; }
            case "pillager" -> { for (int i = 0; i < 6; i++) shoot(p, "arrow", 3.5); sound(p, SoundEvents.CROSSBOW_SHOOT, 1f); return 25; }
            case "piglin" -> { p.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 1)); sound(p, SoundEvents.PIGLIN_ADMIRING_ITEM, 1f); return 100; }
            case "piglin_brute" -> { effect(p, MobEffects.STRENGTH, 200, 2); push(p, look.scale(1.5).add(0, 0.2, 0)); sound(p, SoundEvents.PIGLIN_BRUTE_ANGRY, 1f); return 60; }
            case "zombified_piglin" -> { near(p, 12, e -> { if (e instanceof net.minecraft.world.entity.Mob m && !(e instanceof net.minecraft.world.entity.player.Player)) { LivingEntity t = lookEntity(p, 30); if (t != null) m.setTarget(t); } }); sound(p, SoundEvents.ZOMBIFIED_PIGLIN_ANGRY, 1f); return 100; }
            case "blaze" -> { for (int i = 0; i < 3; i++) shoot(p, "small_fireball", 1.5); sound(p, SoundEvents.BLAZE_SHOOT, 1f); return 15; }
            case "ghast" -> { shoot(p, "fireball", 1.5); sound(p, SoundEvents.GHAST_SHOOT, 1f); return 30; }
            case "happy_ghast" -> { push(p, new Vec3(0, 1.2, 0)); effect(p, MobEffects.SLOW_FALLING, 200, 0); sound(p, SoundEvents.GHAST_AMBIENT, 1.5f); return 20; }
            case "wither_skeleton" -> { shoot(p, "wither_skull", 1.5); sound(p, SoundEvents.WITHER_SHOOT, 1f); return 20; }
            case "snow_golem" -> { for (int i = 0; i < 5; i++) shoot(p, "snowball", 2.0); sound(p, SoundEvents.SNOW_GOLEM_SHOOT, 1f); return 5; }
            case "llama", "trader_llama" -> { shoot(p, "llama_spit", 2.0); sound(p, SoundEvents.LLAMA_SPIT, 1f); return 10; }
            case "breeze" -> { shoot(p, "breeze_wind_charge", 1.5); sound(p, SoundEvents.BREEZE_SHOOT, 1f); return 10; }
            case "witch" -> { effect(p, MobEffects.REGENERATION, 200, 1); near(p, 6, e -> effect(e, MobEffects.WEAKNESS, 160, 1)); sound(p, SoundEvents.WITCH_DRINK, 1f); return 100; }
            case "spider" -> {
                BlockHitResult hit = lookBlock(p, 32);
                if (hit.getType() == HitResult.Type.BLOCK) { BlockPos b = hit.getBlockPos().relative(hit.getDirection()); if (level.getBlockState(b).isAir()) level.setBlockAndUpdate(b, Blocks.COBWEB.defaultBlockState()); }
                sound(p, SoundEvents.SPIDER_AMBIENT, 1f); return 20;
            }
            case "cave_spider" -> { push(p, look.scale(1.4).add(0, 0.5, 0)); LivingEntity t = lookEntity(p, 6); if (t != null) effect(t, MobEffects.POISON, 120, 1); sound(p, SoundEvents.SPIDER_STEP, 1.5f); return 20; }
            case "husk" -> { near(p, 5, e -> effect(e, MobEffects.HUNGER, 200, 2)); sound(p, SoundEvents.HUSK_AMBIENT, 1f); return 60; }
            case "drowned" -> { shoot(p, "trident", 2.5); sound(p, SoundEvents.DROWNED_SHOOT, 1f); return 20; }
            case "zombie_villager" -> { p.heal(6); effect(p, MobEffects.ABSORPTION, 600, 1); sound(p, SoundEvents.ZOMBIE_VILLAGER_CURE, 1f); return 200; }
            case "vindicator" -> { LivingEntity t = lookEntity(p, 5); if (t != null) t.hurtServer(level, p.damageSources().playerAttack(p), 14); sound(p, SoundEvents.VINDICATOR_CELEBRATE, 1f); return 30; }
            case "iron_golem" -> {
                near(p, 5, e -> { e.hurtServer(level, p.damageSources().playerAttack(p), 12); e.setDeltaMovement(e.getDeltaMovement().add(0, 0.8, 0)); e.hurtMarked = true; });
                particles(p, ParticleTypes.EXPLOSION, p.position(), 10, 2, 0); sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8f); return 40;
            }
            case "ravager" -> { push(p, look.scale(2.2).add(0, 0.2, 0)); near(p, 3, e -> { e.hurtServer(level, p.damageSources().playerAttack(p), 10); e.setDeltaMovement(look.scale(2).add(0, 0.5, 0)); e.hurtMarked = true; }); sound(p, SoundEvents.RAVAGER_ROAR, 1f); return 40; }
            case "hoglin", "zoglin" -> { near(p, 4, e -> { e.setDeltaMovement(e.getDeltaMovement().add(0, 1.4, 0)); e.hurtMarked = true; e.hurtServer(level, p.damageSources().playerAttack(p), 6); }); sound(p, SoundEvents.HOGLIN_ATTACK, 1f); return 30; }
            case "warden" -> {
                Vec3 eye = p.getEyePosition();
                for (int i = 1; i < 20; i++) particles(p, ParticleTypes.SONIC_BOOM, eye.add(look.scale(i)), 1, 0, 0);
                LivingEntity t = lookEntity(p, 20);
                if (t != null) t.hurtServer(level, level.damageSources().sonicBoom(p), 15);
                sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1f); return 60;
            }
            case "evoker" -> {
                for (int i = 1; i <= 10; i++) {
                    Vec3 at = p.position().add(look.x * i * 1.3, 0, look.z * i * 1.3);
                    Entity f = type("evoker_fangs").create(level, EntitySpawnReason.TRIGGERED);
                    if (f != null) { f.setPos(at.x, Math.floor(p.getY()), at.z); level.addFreshEntity(f); }
                }
                sound(p, SoundEvents.EVOKER_CAST_SPELL, 1f); return 40;
            }
            case "illusioner" -> { effect(p, MobEffects.INVISIBILITY, 200, 0); near(p, 8, e -> effect(e, MobEffects.BLINDNESS, 100, 0)); sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f); return 100; }
            case "vex" -> { p.setDeltaMovement(look.scale(1.6)); p.hurtMarked = true; effect(p, MobEffects.SLOW_FALLING, 60, 0); sound(p, SoundEvents.VEX_CHARGE, 1f); return 10; }
            case "shulker" -> { LivingEntity t = lookEntity(p, 24); if (t != null) effect(t, MobEffects.LEVITATION, 100, 1); sound(p, SoundEvents.SHULKER_SHOOT, 1f); return 30; }
            case "endermite" -> { Vec3 to = p.position().add(Math.random() * 16 - 8, 0, Math.random() * 16 - 8); p.teleportTo(to.x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) to.x, (int) to.z), to.z); sound(p, SoundEvents.ENDERMITE_AMBIENT, 1f); return 15; }
            case "silverfish" -> { for (int i = 0; i < 3; i++) { Entity f = type("silverfish").create(level, EntitySpawnReason.TRIGGERED); if (f != null) { f.setPos(p.getX(), p.getY(), p.getZ()); level.addFreshEntity(f); } } sound(p, SoundEvents.SILVERFISH_AMBIENT, 1f); return 100; }
            case "squid" -> { near(p, 6, e -> effect(e, MobEffects.BLINDNESS, 100, 0)); particles(p, ParticleTypes.SQUID_INK, p.position().add(0, 1, 0), 80, 1.5, 0.1); sound(p, SoundEvents.SQUID_SQUIRT, 1f); return 40; }
            case "glow_squid" -> { near(p, 12, e -> effect(e, MobEffects.GLOWING, 200, 0)); effect(p, MobEffects.NIGHT_VISION, 1200, 0); particles(p, ParticleTypes.GLOW_SQUID_INK, p.position().add(0, 1, 0), 80, 1.5, 0.1); sound(p, SoundEvents.GLOW_SQUID_SQUIRT, 1f); return 40; }
            case "pufferfish" -> { near(p, 4, e -> effect(e, MobEffects.POISON, 100, 1)); sound(p, SoundEvents.PUFFER_FISH_BLOW_UP, 1f); return 40; }
            case "bee" -> { LivingEntity t = lookEntity(p, 5); if (t != null) { effect(t, MobEffects.POISON, 200, 1); t.hurtServer(level, p.damageSources().playerAttack(p), 4); } push(p, new Vec3(0, 0.6, 0)); sound(p, SoundEvents.BEE_STING, 1f); return 30; }
            case "parrot" -> { push(p, look.scale(1.2).add(0, 0.8, 0)); effect(p, MobEffects.SLOW_FALLING, 100, 0); sound(p, SoundEvents.PARROT_FLY, 1f); return 10; }
            case "bat" -> { effect(p, MobEffects.NIGHT_VISION, 1200, 0); push(p, new Vec3(0, 0.9, 0)); effect(p, MobEffects.SLOW_FALLING, 60, 0); sound(p, SoundEvents.BAT_TAKEOFF, 1f); return 10; }
            case "phantom" -> { p.setDeltaMovement(look.scale(2.0)); p.hurtMarked = true; effect(p, MobEffects.SLOW_FALLING, 100, 0); sound(p, SoundEvents.PHANTOM_SWOOP, 1f); return 15; }
            case "allay" -> { for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(16))) if (e instanceof net.minecraft.world.entity.item.ItemEntity it) it.setPos(p.getX(), p.getY(), p.getZ()); push(p, new Vec3(0, 0.6, 0)); sound(p, SoundEvents.ALLAY_ITEM_GIVEN, 1f); return 40; }
            case "rabbit" -> { push(p, look.scale(0.5).add(0, 1.6, 0)); effect(p, MobEffects.SLOW_FALLING, 40, 0); sound(p, SoundEvents.RABBIT_JUMP, 1f); return 10; }
            case "horse", "skeleton_horse", "zombie_horse" -> { effect(p, MobEffects.SPEED, 200, 3); push(p, look.scale(1.0).add(0, 0.8, 0)); sound(p, SoundEvents.HORSE_GALLOP, 1f); return 40; }
            case "donkey", "mule" -> { effect(p, MobEffects.RESISTANCE, 200, 1); effect(p, MobEffects.SPEED, 200, 1); sound(p, SoundEvents.DONKEY_ANGRY, 1f); return 60; }
            case "camel" -> { push(p, look.scale(2.5).add(0, 0.3, 0)); sound(p, SoundEvents.CAMEL_DASH, 1f); return 40; }
            case "goat" -> { push(p, look.scale(2.0)); near(p, 2.5, e -> { e.setDeltaMovement(look.scale(2.5).add(0, 0.6, 0)); e.hurtMarked = true; e.hurtServer(level, p.damageSources().playerAttack(p), 4); }); sound(p, SoundEvents.GOAT_RAM_IMPACT, 1f); return 40; }
            case "frog" -> { LivingEntity t = lookEntity(p, 6); if (t != null && t.getMaxHealth() <= 10 && !(t instanceof net.minecraft.world.entity.player.Player)) t.kill(level); else push(p, look.scale(0.6).add(0, 1.3, 0)); sound(p, SoundEvents.FROG_TONGUE, 1f); return 20; }
            case "slime" -> { push(p, new Vec3(0, 1.8, 0)); effect(p, MobEffects.SLOW_FALLING, 30, 0); sound(p, SoundEvents.SLIME_JUMP, 1f); return 15; }
            case "magma_cube" -> { push(p, new Vec3(0, 1.6, 0)); near(p, 3, e -> e.igniteForSeconds(4)); effect(p, MobEffects.FIRE_RESISTANCE, 200, 0); sound(p, SoundEvents.MAGMA_CUBE_JUMP, 1f); return 20; }
            case "wolf" -> { push(p, look.scale(1.8).add(0, 0.4, 0)); LivingEntity t = lookEntity(p, 4); if (t != null) t.hurtServer(level, p.damageSources().playerAttack(p), 8); sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 1f); return 25; }
            case "fox" -> { effect(p, MobEffects.SPEED, 200, 2); effect(p, MobEffects.INVISIBILITY, 60, 0); sound(p, SoundEvents.FOX_SCREECH, 1f); return 60; }
            case "cat" -> { near(p, 10, e -> { if (e.getType() == type("creeper") || e.getType() == type("phantom")) e.setDeltaMovement(e.position().subtract(p.position()).normalize().scale(2)); }); effect(p, MobEffects.JUMP_BOOST, 200, 2); sound(p, SoundEvents.CAT_HISS, 1f); return 40; }
            case "ocelot" -> { effect(p, MobEffects.SPEED, 200, 4); sound(p, SoundEvents.OCELOT_AMBIENT, 1f); return 60; }
            case "panda" -> { push(p, look.scale(1.5)); near(p, 2.5, e -> { e.setDeltaMovement(look.scale(1.5).add(0, 0.4, 0)); e.hurtMarked = true; }); sound(p, SoundEvents.PANDA_SNEEZE, 1f); return 30; }
            case "polar_bear" -> { near(p, 4, e -> { e.hurtServer(level, p.damageSources().playerAttack(p), 9); effect(e, MobEffects.SLOWNESS, 100, 1); }); sound(p, SoundEvents.POLAR_BEAR_WARNING, 1f); return 40; }
            case "armadillo" -> { effect(p, MobEffects.RESISTANCE, 160, 3); effect(p, MobEffects.SLOWNESS, 160, 2); sound(p, SoundEvents.ARMADILLO_ROLL, 1f); return 100; }
            case "dolphin" -> { effect(p, MobEffects.DOLPHINS_GRACE, 400, 0); push(p, look.scale(2.0)); sound(p, SoundEvents.DOLPHIN_PLAY, 1f); return 20; }
            case "axolotl" -> { effect(p, MobEffects.REGENERATION, 200, 1); effect(p, MobEffects.WATER_BREATHING, 1200, 0); sound(p, SoundEvents.AXOLOTL_SPLASH, 1f); return 100; }
            case "turtle" -> { effect(p, MobEffects.RESISTANCE, 200, 2); effect(p, MobEffects.SLOWNESS, 200, 3); effect(p, MobEffects.WATER_BREATHING, 1200, 0); sound(p, SoundEvents.TURTLE_SHAMBLE, 1f); return 100; }
            case "__fish_unused__" -> { effect(p, MobEffects.WATER_BREATHING, 1200, 0); effect(p, MobEffects.DOLPHINS_GRACE, 200, 0); push(p, look.scale(1.2)); sound(p, SoundEvents.FISH_SWIM, 1f); return 20; }
            case "guardian" -> { LivingEntity t = lookEntity(p, 16); if (t != null) t.hurtServer(level, p.damageSources().magic(), 8); sound(p, SoundEvents.GUARDIAN_ATTACK, 1f); return 30; }
            case "elder_guardian" -> { near(p, 16, e -> effect(e, MobEffects.MINING_FATIGUE, 600, 2)); sound(p, SoundEvents.ELDER_GUARDIAN_CURSE, 1f); return 100; }
            case "strider" -> { effect(p, MobEffects.FIRE_RESISTANCE, 1200, 0); sound(p, SoundEvents.STRIDER_HAPPY, 1f); return 100; }
            case "villager" -> { p.addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 1)); effect(p, MobEffects.HERO_OF_THE_VILLAGE, 1200, 0); sound(p, SoundEvents.VILLAGER_YES, 1f); return 200; }
            case "wandering_trader" -> { effect(p, MobEffects.INVISIBILITY, 300, 0); sound(p, SoundEvents.WANDERING_TRADER_DISAPPEARED, 1f); return 100; }
            case "creaking" -> { near(p, 8, e -> effect(e, MobEffects.SLOWNESS, 100, 5)); sound(p, SoundEvents.CREAKING_FREEZE, 1f); return 60; }
            case "copper_golem" -> { LivingEntity t = lookEntity(p, 20); if (t != null) { var bolt = type("lightning_bolt").create(level, EntitySpawnReason.TRIGGERED); if (bolt != null) { bolt.setPos(t.getX(), t.getY(), t.getZ()); level.addFreshEntity(bolt); } } sound(p, SoundEvents.COPPER_BREAK, 1f); return 60; }
            default -> {
                // Simple mobs like pigs, sheep and fish have no superpower
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal("This mob has no superpower.").withStyle(net.minecraft.ChatFormatting.GRAY));
                return 20;
            }
        }
    }
}
