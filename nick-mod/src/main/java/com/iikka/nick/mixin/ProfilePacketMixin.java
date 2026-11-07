package com.iikka.nick.mixin;

import com.iikka.nick.NickMod;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The tab list and other players get your nick and chosen skin. */
@Mixin(ClientboundPlayerInfoUpdatePacket.class)
public class ProfilePacketMixin {
    @Shadow @Final @Mutable private List<ClientboundPlayerInfoUpdatePacket.Entry> entries;

    @Inject(method = {
        "<init>(Ljava/util/EnumSet;Ljava/util/Collection;)V",
        "<init>(Lnet/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$Action;Lnet/minecraft/server/level/ServerPlayer;)V"
    }, at = @At("RETURN"))
    private void nick$disguise(CallbackInfo ci) {
        this.entries = NickMod.disguise(this.entries);
    }
}
