package com.iikka.nick;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tells your own game to reload your skin. */
public record RefreshPayload() implements CustomPacketPayload {
    public static final Type<RefreshPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("nick", "refresh"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RefreshPayload> CODEC = StreamCodec.unit(new RefreshPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
