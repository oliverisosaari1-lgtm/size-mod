package com.iikka.spider;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The game tells the server "I'm web swinging", so you don't take fall damage. */
public record SafePayload() implements CustomPacketPayload {
    public static final Type<SafePayload> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath("spiderman", "safe"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SafePayload> CODEC = StreamCodec.unit(new SafePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
