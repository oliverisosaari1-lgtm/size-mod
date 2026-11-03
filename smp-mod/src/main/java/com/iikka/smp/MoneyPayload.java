package com.iikka.smp;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server tells the player's game how much money they have, so it can draw the money box. */
public record MoneyPayload(long money, boolean show) implements CustomPacketPayload {
    public static final Type<MoneyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("donutsmp", "money"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoneyPayload> CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_LONG, MoneyPayload::money,
        ByteBufCodecs.BOOL, MoneyPayload::show,
        MoneyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
