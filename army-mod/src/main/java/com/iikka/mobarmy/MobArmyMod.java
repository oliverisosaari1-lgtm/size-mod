package com.iikka.mobarmy;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/** Mobs you kill come back as your pets: they follow you and fight whatever you fight. */
public class MobArmyMod implements ModInitializer {
    private static final String PET = "mobarmy_pet", OWNER = "mobarmy_owner:";
    private static final Set<Mob> pets = Collections.newSetFromMap(new WeakHashMap<>());

    static UUID owner(Entity e) {
        for (String t : e.getTags()) if (t.startsWith(OWNER)) try { return UUID.fromString(t.substring(OWNER.length())); } catch (Exception ignored) { }
        return null;
    }

    static boolean sameTeam(Entity a, UUID owner) {
        if (a == null || owner == null) return false;
        if (a.getUUID().equals(owner)) return true;
        return owner.equals(owner(a));
    }

    private static void revive(Mob dead, ServerPlayer p) {
        ServerLevel level = (ServerLevel) dead.level();
        Entity e = dead.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (!(e instanceof Mob pet)) return;
        pet.snapTo(dead.getX(), dead.getY(), dead.getZ(), dead.getYRot(), 0);
        pet.addTag(PET);
        pet.addTag(OWNER + p.getUUID());
        pet.setPersistenceRequired();
        pet.setCustomName(Component.literal(p.getName().getString() + "'s ").append(dead.getType().getDescription()).withStyle(ChatFormatting.AQUA));
        level.addFreshEntity(pet);
        level.sendParticles(ParticleTypes.SOUL, pet.getX(), pet.getY() + 1, pet.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
        level.playSound(null, pet.getX(), pet.getY(), pet.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.5f, 1.4f);
        p.displayClientMessage(Component.literal("A ").append(dead.getType().getDescription()).append(" joined your army!").withStyle(ChatFormatting.AQUA), true);
    }

    private static LivingEntity enemyOf(ServerPlayer o) {
        LivingEntity hit = o.getLastHurtMob();
        if (hit != null && hit.isAlive() && o.tickCount - o.getLastHurtMobTimestamp() < 200 && !sameTeam(hit, o.getUUID())) return hit;
        LivingEntity by = o.getLastHurtByMob();
        if (by != null && by.isAlive() && !sameTeam(by, o.getUUID())) return by;
        return null;
    }

    private static void setTarget(Mob m, LivingEntity t) {
        m.setTarget(t);
        try {
            if (t == null) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            else if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
                m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, t);
        } catch (Throwable ignored) { }
    }

    private static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        for (Mob m : new ArrayList<>(pets)) {
            if (m.isRemoved() || !m.isAlive()) { pets.remove(m); continue; }
            UUID id = owner(m);
            ServerPlayer o = id == null ? null : server.getPlayerList().getPlayer(id);
            // Never fight the owner or the owner's other pets
            if (sameTeam(m.getTarget(), id)) setTarget(m, null);
            if (o == null || o.level() != m.level() || o.isSpectator()) continue;
            if ((now + m.getId()) % 10 != 0) continue;

            LivingEntity enemy = enemyOf(o);
            if (enemy != null && enemy.level() == m.level() && enemy.distanceTo(m) < 32) {
                if (m.getTarget() != enemy) setTarget(m, enemy);
                // Mobs that can't normally attack (pigs, sheep...) still run in and bite
                if (m.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
                    m.getNavigation().moveTo(enemy, 1.3);
                    if (m.distanceTo(enemy) < m.getBbWidth() + 1.5) enemy.hurtServer((ServerLevel) m.level(), m.damageSources().mobAttack(m), 3);
                }
                continue;
            }
            if (m.getTarget() != null && !m.getTarget().isAlive()) setTarget(m, null);
            double d = m.distanceTo(o);
            if (d > 32) m.teleportTo(o.getX(), o.getY(), o.getZ());
            else if (d > 6 && m.getTarget() == null) m.getNavigation().moveTo(o, 1.2);
        }
    }

    @Override
    public void onInitialize() {
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> { if (e instanceof Mob m && e.getTags().contains(PET)) pets.add(m); });
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> { if (e instanceof Mob m) pets.remove(m); });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Mob dead) || entity.getTags().contains(PET)) return;
            if (dead.getType() == EntityType.ENDER_DRAGON) return;
            if (source.getEntity() instanceof ServerPlayer p) revive(dead, p);
        });
        // Your pets can't hurt you, and their arrows/explosions don't hurt you either
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, source, amount) -> {
            UUID by = source.getEntity() == null ? null : owner(source.getEntity());
            if (by == null) return true;
            return !(victim.getUUID().equals(by) || by.equals(owner(victim)));
        });
        // You can't hit your own pets by accident
        AttackEntityCallback.EVENT.register((p, w, h, target, hit) ->
            !w.isClientSide() && p.getUUID().equals(owner(target)) ? InteractionResult.FAIL : InteractionResult.PASS);
        ServerTickEvents.END_SERVER_TICK.register(MobArmyMod::tick);
    }
}
