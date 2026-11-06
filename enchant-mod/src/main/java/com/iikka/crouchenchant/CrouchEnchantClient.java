package com.iikka.crouchenchant;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;

public class CrouchEnchantClient implements ClientModInitializer {
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
            if (renderer instanceof net.minecraft.client.renderer.entity.player.AvatarRenderer r)
                helper.register(new WornItemLayer((net.minecraft.client.renderer.entity.RenderLayerParent) r, ctx.getItemModelResolver()));
        });
        KeyMapping key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.crouchenchant.detach", InputConstants.KEY_L, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) {
                if (mc.screen == null && mc.player != null && ClientPlayNetworking.canSend(DetachPayload.TYPE)) ClientPlayNetworking.send(new DetachPayload());
            }
        });
    }
}
