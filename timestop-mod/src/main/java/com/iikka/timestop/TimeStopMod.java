package com.iikka.timestop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * G stops time: Minecraft's own freeze stops mobs, arrows, items, water, fire, crops, redstone,
 * weather and day/night. Other players are held in place too, so only you can move.
 */
public class TimeStopMod implements ModInitializer {
    private static UUID stopper;
    private static final Map<UUID, double[]> held = new HashMap<>(); // x, y, z, yRot, xRot

    static boolean frozenPlayer(Player p) {
        return stopper != null && !p.getUUID().equals(stopper) && !p.level().isClientSide();
    }

    static void set(MinecraftServer server, ServerPlayer by, boolean stop) {
        stopper = stop ? by.getUUID() : null;
        held.clear();
        server.tickRateManager().setFrozen(stop);
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            o.sendSystemMessage(Component.literal(stop ? by.getName().getString() + " stopped time!" : "Time moves again.")
                .withStyle(stop ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        }
        by.level().playSound(null, by.getX(), by.getY(), by.getZ(), stop ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, stop ? 0.6f : 1.2f);
    }

    static void toggle(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        if (stopper == null) set(server, p, true);
        else if (stopper.equals(p.getUUID())) set(server, p, false);
        // someone else is frozen: they can't start time again
    }

    private static void tick(MinecraftServer server) {
        if (stopper == null) return;
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o.getUUID().equals(stopper) || o.isSpectator()) continue;
            double[] h = held.computeIfAbsent(o.getUUID(), k -> new double[]{o.getX(), o.getY(), o.getZ(), o.getYRot(), o.getXRot()});
            o.setDeltaMovement(0, 0, 0);
            o.fallDistance = 0;
            if (o.distanceToSqr(h[0], h[1], h[2]) > 0.0001 || Math.abs(o.getYRot() - h[3]) > 0.1 || Math.abs(o.getXRot() - h[4]) > 0.1)
                o.connection.teleport(h[0], h[1], h[2], (float) h[3], (float) h[4]);
        }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(TogglePayload.TYPE, TogglePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TogglePayload.TYPE, (payload, ctx) -> toggle(ctx.player()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) ->
            d.register(Commands.literal("timestop").executes(ctx -> { toggle(ctx.getSource().getPlayerOrException()); return 1; })));

        ServerTickEvents.END_SERVER_TICK.register(TimeStopMod::tick);
        // If the one who stopped time leaves, time starts again
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> {
            if (h.player.getUUID().equals(stopper)) s.execute(() -> { if (stopper != null) set(s, h.player, false); });
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { stopper = null; held.clear(); s.tickRateManager().setFrozen(false); });

        // Frozen players can't hit, break, place or use anything
        AttackBlockCallback.EVENT.register((p, w, h, pos, dir) -> frozenPlayer(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        AttackEntityCallback.EVENT.register((p, w, h, e, hit) -> frozenPlayer(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseBlockCallback.EVENT.register((p, w, h, hit) -> frozenPlayer(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((p, w, h) -> frozenPlayer(p) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseEntityCallback.EVENT.register((p, w, h, e, hit) -> frozenPlayer(p) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
