package com.iikka.morph;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** V was pressed: use the superpower. */
public record PowerPayload() implements CustomPacketPayload {
    public static final Type<PowerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "power"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PowerPayload> CODEC = StreamCodec.unit(new PowerPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
