package com.iikka.move;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class MoveClient implements ClientModInitializer {
    private static KeyMapping dashKey;
    private static boolean jumpReleased, doubleJumpUsed, dashUsedInAir;
    private static int airTicks, dashCooldown;

    @Override
    public void onInitializeClient() {
        dashKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.olkkumove.dash", InputConstants.KEY_Q, KeyMapping.Category.MOVEMENT));
        ClientPlayNetworking.registerGlobalReceiver(AnimPayload.TYPE, (payload, ctx) -> {
            Anim.start(payload.entityId(), payload.kind());
            Entity e = ctx.client().level == null ? null : ctx.client().level.getEntity(payload.entityId());
            if (e != null && payload.kind() == Anim.FLIP) puff(ctx.client(), e, 10);
        });
        // Runs before the game handles keys, so a dash can stop Q from also dropping your item.
        ClientTickEvents.START_CLIENT_TICK.register(MoveClient::handleDashKey);
        ClientTickEvents.END_CLIENT_TICK.register(MoveClient::tick);
    }

    private static boolean moving(LocalPlayer p) {
        return p.input != null && p.input.getMoveVector().lengthSquared() > 0.01f;
    }

    private static void handleDashKey(Minecraft mc) {
        LocalPlayer p = mc.player;
        boolean sameAsDrop = dashKey.same(mc.options.keyDrop);
        while (dashKey.consumeClick()) {
            if (p == null || mc.screen != null || !moving(p)) continue; // standing still: Q drops like normal
            if (sameAsDrop) while (mc.options.keyDrop.consumeClick()) { }
            dash(mc, p);
        }
    }

    private static void dash(Minecraft mc, LocalPlayer p) {
        if (dashCooldown > 0 || p.isPassenger() || p.isSpectator() || p.getAbilities().flying || p.isFallFlying()) return;
        if (!p.onGround() && dashUsedInAir) return;
        Vec2 mv = p.input.getMoveVector(); // x = left/right, y = forward/back
        double yaw = Math.toRadians(p.getYRot());
        double s = Math.sin(yaw), c = Math.cos(yaw);
        Vec3 dir = new Vec3(mv.x * c - mv.y * s, 0, mv.y * c + mv.x * s).normalize();
        double power = p.onGround() ? 1.55 : 1.15;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(dir.x * power, p.onGround() ? 0.22 : Math.max(v.y, 0.12), dir.z * power);
        if (!p.onGround()) dashUsedInAir = true;
        dashCooldown = 22;
        Anim.start(p.getId(), Anim.DASH);
        if (ClientPlayNetworking.canSend(MovePayload.TYPE)) ClientPlayNetworking.send(new MovePayload(Anim.DASH));
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        if (dashCooldown > 0) dashCooldown--;

        // Double jump: press jump again while in the air.
        boolean jumpDown = mc.options.keyJump.isDown();
        if (p.onGround() || p.isInWater() || p.onClimbable() || p.isPassenger()) {
            airTicks = 0; jumpReleased = false; doubleJumpUsed = false; dashUsedInAir = false;
        } else {
            airTicks++;
            if (!jumpDown) jumpReleased = true;
            else if (jumpReleased && !doubleJumpUsed && airTicks > 2 && mc.screen == null
                && !p.getAbilities().mayfly && !p.isFallFlying() && !p.isSpectator()) {
                doubleJumpUsed = true;
                Vec3 v = p.getDeltaMovement();
                Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
                double push = moving(p) ? 0.18 : 0;
                p.setDeltaMovement(v.x + look.x * push, 0.58, v.z + look.z * push);
                Anim.start(p.getId(), Anim.FLIP);
                puff(mc, p, 10);
                if (ClientPlayNetworking.canSend(MovePayload.TYPE)) ClientPlayNetworking.send(new MovePayload(Anim.FLIP));
            }
        }

        // Wind trail behind anyone dashing.
        for (var en : Anim.all().entrySet()) {
            if (en.getValue()[0] != Anim.DASH) continue;
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null) continue;
            for (int i = 0; i < 3; i++)
                mc.level.addParticle(ParticleTypes.CLOUD, e.getX() + (Math.random() - 0.5) * 0.5, e.getY() + 0.2 + Math.random() * 1.2,
                    e.getZ() + (Math.random() - 0.5) * 0.5, 0, 0.01, 0);
        }
    }

    private static void puff(Minecraft mc, Entity e, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Mth.TWO_PI / n;
            mc.level.addParticle(ParticleTypes.CLOUD, e.getX() + Math.cos(a) * 0.4, e.getY() + 0.05, e.getZ() + Math.sin(a) * 0.4,
                Math.cos(a) * 0.08, 0.0, Math.sin(a) * 0.08);
        }
    }
}
