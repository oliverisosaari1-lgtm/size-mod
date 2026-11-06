package com.iikka.crouchenchant;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** L was pressed: take the enchants off the held item. */
public record DetachPayload() implements CustomPacketPayload {
    public static final Type<DetachPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("crouchenchant", "detach"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DetachPayload> CODEC = StreamCodec.unit(new DetachPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
