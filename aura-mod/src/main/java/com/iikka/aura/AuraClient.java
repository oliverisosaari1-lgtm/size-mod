package com.iikka.aura;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class AuraClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aura.ring", InputConstants.KEY_G, KeyMapping.Category.MISC));
        KeyMapping laser = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aura.laser", InputConstants.KEY_B, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            // Hold B to fire the laser (after /laser)
            if (mc.screen == null && mc.player != null && laser.isDown() && ClientPlayNetworking.canSend(LaserPayload.TYPE))
                ClientPlayNetworking.send(new LaserPayload());
            // Hold G to keep shooting rings
            if (mc.screen == null && mc.player != null && key.isDown() && mc.player.tickCount % 2 == 0 && ClientPlayNetworking.canSend(RingPayload.TYPE))
                ClientPlayNetworking.send(new RingPayload());
        });
    }
}
