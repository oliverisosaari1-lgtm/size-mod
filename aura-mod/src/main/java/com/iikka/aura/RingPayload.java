package com.iikka.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** G is held: shoot a ring. */
public record RingPayload() implements CustomPacketPayload {
    public static final Type<RingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("aura", "ring"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingPayload> CODEC = StreamCodec.unit(new RingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
