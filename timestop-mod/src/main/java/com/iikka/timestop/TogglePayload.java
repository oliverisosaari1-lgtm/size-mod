package com.iikka.timestop;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** G was pressed: stop or start time. */
public record TogglePayload() implements CustomPacketPayload {
    public static final Type<TogglePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("timestop", "toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC = StreamCodec.unit(new TogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
