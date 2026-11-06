package com.iikka.aura;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** B is held: fire the laser. */
public record LaserPayload() implements CustomPacketPayload {
    public static final Type<LaserPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("aura", "laser"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LaserPayload> CODEC = StreamCodec.unit(new LaserPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
