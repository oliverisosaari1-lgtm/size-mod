package com.iikka.cloak;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** H was pressed: turn invisible on/off. */
public record TogglePayload() implements CustomPacketPayload {
    public static final Type<TogglePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("cloak", "toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC = StreamCodec.unit(new TogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
