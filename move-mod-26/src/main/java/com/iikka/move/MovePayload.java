package com.iikka.move;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client did a move: 1 = double jump, 2 = dash. */
public record MovePayload(byte kind) implements CustomPacketPayload {
    public static final Type<MovePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("olkkumove", "move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovePayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.BYTE, MovePayload::kind, MovePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
