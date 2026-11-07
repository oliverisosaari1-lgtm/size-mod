package com.iikka.move;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Show another player's flip or dash animation. */
public record AnimPayload(int entityId, byte kind) implements CustomPacketPayload {
    public static final Type<AnimPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("olkkumove", "anim"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnimPayload> CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, AnimPayload::entityId, ByteBufCodecs.BYTE, AnimPayload::kind, AnimPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
