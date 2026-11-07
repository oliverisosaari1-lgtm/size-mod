package com.iikka.nick;

import com.iikka.nick.mixin.ClientPlayerAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class NickClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Forget the old skin so your own player (F5 and inventory) uses the new one.
        ClientPlayNetworking.registerGlobalReceiver(RefreshPayload.TYPE, (payload, ctx) -> {
            if (ctx.client().player != null) ((ClientPlayerAccess) ctx.client().player).nick$setPlayerInfo(null);
        });
    }
}
