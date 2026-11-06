package com.iikka.morph;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MorphClient implements ClientModInitializer {
    private static KeyMapping key, powerKey;
    private static Map<UUID, String> morphs = new HashMap<>();
    private static final Map<UUID, LivingEntity> bodies = new HashMap<>();

    /** The mob body used to draw this player, moved and posed exactly like the player. Null if not morphed. */
    public static LivingEntity bodyFor(Player p) {
        String id = morphs.get(p.getUUID());
        if (id == null) return null;
        try {
            LivingEntity mob = bodies.get(p.getUUID());
            if (mob == null || mob.level() != p.level() || !id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString())) {
                Entity e = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)).create(p.level(), EntitySpawnReason.LOAD);
                if (!(e instanceof LivingEntity le)) return null;
                mob = le;
                bodies.put(p.getUUID(), mob);
            }
            copyPose(p, mob);
            return mob;
        } catch (Throwable t) {
            return null; // never crash the game, just draw the normal player
        }
    }

    private static void copyPose(Player p, LivingEntity m) {
        m.setPos(p.getX(), p.getY(), p.getZ());
        m.xo = p.xo; m.yo = p.yo; m.zo = p.zo;
        m.xOld = p.xOld; m.yOld = p.yOld; m.zOld = p.zOld;
        m.setYRot(p.getYRot()); m.yRotO = p.yRotO;
        m.setXRot(p.getXRot()); m.xRotO = p.xRotO;
        m.yBodyRot = p.yBodyRot; m.yBodyRotO = p.yBodyRotO;
        m.yHeadRot = p.yHeadRot; m.yHeadRotO = p.yHeadRotO;
        m.tickCount = p.tickCount;
        m.setOnGround(p.onGround());
        m.setShiftKeyDown(p.isShiftKeyDown());
        m.setSprinting(p.isSprinting());
        m.setInvisible(p.isInvisible());
        m.swinging = p.swinging; m.swingTime = p.swingTime; m.swingingArm = p.swingingArm;
        m.attackAnim = p.attackAnim; m.oAttackAnim = p.oAttackAnim;
        m.hurtTime = p.hurtTime; m.hurtDuration = p.hurtDuration;
        m.deathTime = p.deathTime;
        m.setRemainingFireTicks(p.getRemainingFireTicks());
        copyWalk(p.walkAnimation, m.walkAnimation);
    }

    private static Field[] walkFields;
    private static void copyWalk(WalkAnimationState from, WalkAnimationState to) {
        try {
            if (walkFields == null) {
                walkFields = java.util.Arrays.stream(WalkAnimationState.class.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers())).toArray(Field[]::new);
                for (Field f : walkFields) f.setAccessible(true);
            }
            for (Field f : walkFields) f.set(to, f.get(from));
        } catch (Throwable ignored) { }
    }

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, ctx) -> {
            morphs = new HashMap<>(payload.morphs());
            bodies.keySet().retainAll(morphs.keySet());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> { morphs = new HashMap<>(); bodies.clear(); });

        key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.morph.menu", InputConstants.KEY_RSHIFT, KeyMapping.Category.MISC));
        powerKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.morph.power", InputConstants.KEY_V, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (powerKey.consumeClick()) {
                if (mc.screen == null && mc.player != null && ClientPlayNetworking.canSend(PowerPayload.TYPE)) ClientPlayNetworking.send(new PowerPayload());
            }
            while (key.consumeClick()) {
                if (mc.screen == null && mc.player != null && ClientPlayNetworking.canSend(OpenPayload.TYPE)) ClientPlayNetworking.send(new OpenPayload());
            }
        });
    }
}
