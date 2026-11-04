package com.iikka.spider;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public class SpiderMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(SafePayload.TYPE, SafePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SafePayload.TYPE, (payload, context) -> context.player().fallDistance = 0);
    }
}
