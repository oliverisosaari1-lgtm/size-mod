package com.iikka.practice;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** K was pressed: open the practice menu. */
public record MenuPayload() implements CustomPacketPayload {
    public static final Type<MenuPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("olkkuspractice", "menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuPayload> CODEC = StreamCodec.unit(new MenuPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
