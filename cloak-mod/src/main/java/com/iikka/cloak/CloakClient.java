package com.iikka.cloak;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class CloakClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cloak.toggle", InputConstants.KEY_H, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (mc.screen == null && mc.player != null && ClientPlayNetworking.canSend(TogglePayload.TYPE)) ClientPlayNetworking.send(new TogglePayload());
            }
        });
    }
}
