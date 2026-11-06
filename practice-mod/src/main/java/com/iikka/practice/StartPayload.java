package com.iikka.practice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** From the main menu: what to start after the world loads. mode = "bot" or "duel". */
public record StartPayload(String mode, String kit, String difficulty, String loot, String arena) implements CustomPacketPayload {
    public static final Type<StartPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("olkkuspractice", "start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StartPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, StartPayload::mode,
        ByteBufCodecs.STRING_UTF8, StartPayload::kit,
        ByteBufCodecs.STRING_UTF8, StartPayload::difficulty,
        ByteBufCodecs.STRING_UTF8, StartPayload::loot,
        ByteBufCodecs.STRING_UTF8, StartPayload::arena,
        StartPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
