package com.iikka.smp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server asks the player's game to open one of the mod's screens. data is extra info, lines split by \n. */
public record ScreenPayload(String screen, String data) implements CustomPacketPayload {
    public static final Type<ScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("donutsmp", "screen"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ScreenPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, ScreenPayload::screen,
        ByteBufCodecs.STRING_UTF8, ScreenPayload::data,
        ScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
