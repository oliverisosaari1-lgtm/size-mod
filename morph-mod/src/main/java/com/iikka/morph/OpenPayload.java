package com.iikka.morph;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Right Shift was pressed: please open the morph menu. */
public record OpenPayload() implements CustomPacketPayload {
    public static final Type<OpenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "open"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.unit(new OpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
