package com.iikka.morph;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server tells every client who is morphed into what (player uuid -> mob id). */
public record SyncPayload(Map<UUID, String> morphs) implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("morph", "sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> CODEC = ByteBufCodecs.<RegistryFriendlyByteBuf, UUID, String, Map<UUID, String>>map(HashMap::new, UUIDUtil.STREAM_CODEC.cast(), ByteBufCodecs.STRING_UTF8.cast())
        .map(SyncPayload::new, SyncPayload::morphs);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
