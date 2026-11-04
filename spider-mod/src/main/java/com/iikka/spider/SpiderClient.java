package com.iikka.spider;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;

public class SpiderClient implements ClientModInitializer {
    private static final double RANGE = 80;
    private static KeyMapping webKey;
    private static Vec3 target = null; // where the web is stuck
    private static int webTicks = 0;
    private static int safeTicks = 0;  // no fall damage for a bit after letting go

    @Override
    public void onInitializeClient() {
        webKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.spiderman.web", InputConstants.KEY_F, KeyMapping.Category.MOVEMENT));

        // F is "swap hands" in normal Minecraft. Unbind that once so F only shoots webs.
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> {
            try {
                Path flag = mc.gameDirectory.toPath().resolve("config").resolve("spiderman-setup.txt");
                if (Files.exists(flag)) return;
                Files.createDirectories(flag.getParent());
                Files.writeString(flag, "done");
                if (mc.options.keySwapOffhand.same(webKey)) {
                    mc.options.keySwapOffhand.setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                    mc.options.save();
                }
            } catch (Exception ignored) { }
        });

        ClientTickEvents.END_CLIENT_TICK.register(SpiderClient::tick);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) { target = null; return; }

        while (webKey.consumeClick()) {
            if (target != null) { target = null; continue; } // press again to let go
            HitResult hit = p.pick(RANGE, 1.0f, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                target = ((BlockHitResult) hit).getLocation();
                webTicks = 0;
                p.playSound(SoundEvents.FISHING_BOBBER_THROW, 1f, 1.4f);
            }
        }

        if (target != null) {
            webTicks++;
            Vec3 me = p.position().add(0, 1, 0);
            Vec3 toTarget = target.subtract(me);
            double dist = toTarget.length();
            if (dist < 1.8 || webTicks > 100 || p.isShiftKeyDown()) {
                target = null;
            } else {
                // Pull toward the web, keep some of the old speed so it feels like swinging
                Vec3 pull = toTarget.normalize().scale(Math.min(1.6, 0.4 + dist * 0.08));
                Vec3 v = p.getDeltaMovement().scale(0.55).add(pull.scale(0.55));
                p.setDeltaMovement(v);
                p.fallDistance = 0;
                // The web line
                Vec3 hand = p.getEyePosition().add(p.getLookAngle().scale(0.4)).add(0, -0.3, 0);
                Vec3 line = target.subtract(hand);
                int steps = (int) Math.min(60, line.length() * 1.5);
                for (int i = 0; i <= steps; i++) {
                    Vec3 pt = hand.add(line.scale(i / (double) steps));
                    mc.level.addParticle(ParticleTypes.ITEM_SNOWBALL, pt.x, pt.y, pt.z, 0, 0, 0);
                }
            }
            safeTicks = 60;
        }

        if (safeTicks > 0) {
            if (p.onGround() && target == null) safeTicks = Math.min(safeTicks, 5);
            safeTicks--;
            p.fallDistance = 0;
            if (ClientPlayNetworking.canSend(SafePayload.TYPE)) ClientPlayNetworking.send(new SafePayload());
        }
    }
}
