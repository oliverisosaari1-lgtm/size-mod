package com.iikka.nick.mixin;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Your own name tag (F5) shows the nick the server gave you. */
@Mixin(Player.class)
public class ClientNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void nick$clientName(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof AbstractClientPlayer p) {
            PlayerInfo info = ((ClientPlayerAccess) p).nick$getPlayerInfo();
            if (info != null && info.getProfile() != null && !info.getProfile().name().equals(p.getGameProfile().name()))
                cir.setReturnValue(Component.literal(info.getProfile().name()));
        }
    }
}
