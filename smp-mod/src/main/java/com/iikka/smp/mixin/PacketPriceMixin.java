package com.iikka.smp.mixin;

import com.iikka.smp.VanillaView;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Adds "~$ price" to items sent to players who don't have the mod.
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PacketPriceMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V",
        at = @At("HEAD"), argsOnly = true, require = 0)
    private Packet<?> donutsmp$prices(Packet<?> packet) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game) return VanillaView.rewrite(game.player, packet);
        return packet;
    }
}
