package com.iikka.morph;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class MorphClient implements ClientModInitializer {
    private static KeyMapping key, powerKey;

    @Override
    public void onInitializeClient() {
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
