package com.iikka.cloak;

import com.iikka.cloak.mixin.ChunkMapAccess;
import com.iikka.cloak.mixin.TrackedEntityAccess;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CloakMod implements ModInitializer {
    private static final Set<UUID> hidden = new HashSet<>();

    public static boolean isHidden(ServerPlayer p) {
        return hidden.contains(p.getUUID());
    }

    /** Make the other players' games add/remove you right now. */
    private static void refresh(ServerPlayer p) {
        try {
            ServerLevel level = (ServerLevel) p.level();
            Object tracked = ((ChunkMapAccess) level.getChunkSource().chunkMap).cloak$entityMap().get(p.getId());
            if (tracked == null) return;
            for (ServerPlayer o : level.players()) if (o != p) ((TrackedEntityAccess) tracked).cloak$updatePlayer(o);
        } catch (Throwable ignored) { }
    }

    static void toggle(ServerPlayer p) {
        boolean hide = !hidden.remove(p.getUUID());
        if (hide) hidden.add(p.getUUID());
        refresh(p);
        p.sendSystemMessage(Component.literal(hide ? "You are invisible to everyone." : "You are visible again.")
            .withStyle(hide ? ChatFormatting.DARK_PURPLE : ChatFormatting.GRAY));
    }

    private static void tick(MinecraftServer s) {
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!isHidden(p)) continue;
            if (s.getTickCount() % 40 == 0) p.displayClientMessage(Component.literal("Invisible").withStyle(ChatFormatting.DARK_PURPLE), true);
            // Mobs that were already after you forget you
            for (Entity e : p.level().getEntities(p, p.getBoundingBox().inflate(64))) {
                if (!(e instanceof Mob m)) continue;
                if (m.getTarget() == p) m.setTarget(null);
                var brain = m.getBrain();
                if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == p)
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
        }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(TogglePayload.TYPE, TogglePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TogglePayload.TYPE, (payload, ctx) -> toggle(ctx.player()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) ->
            d.register(Commands.literal("invisible").executes(ctx -> { toggle(ctx.getSource().getPlayerOrException()); return 1; })));
        ServerTickEvents.END_SERVER_TICK.register(CloakMod::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> hidden.remove(h.player.getUUID()));
    }
}
