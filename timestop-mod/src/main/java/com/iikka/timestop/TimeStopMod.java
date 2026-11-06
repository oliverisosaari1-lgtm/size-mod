package com.iikka.timestop;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Uses Minecraft's own freeze: everything stops (mobs, arrows, falling blocks...), players can still move. */
public class TimeStopMod implements ModInitializer {
    static void toggle(ServerPlayer p) {
        var tick = p.level().getServer().tickRateManager();
        boolean stop = !tick.isFrozen();
        tick.setFrozen(stop);
        var server = p.level().getServer();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            o.sendSystemMessage(Component.literal(stop ? p.getName().getString() + " stopped time!" : "Time moves again.")
                .withStyle(stop ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY));
        }
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), stop ? SoundEvents.BEACON_DEACTIVATE : SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, stop ? 0.6f : 1.2f);
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(TogglePayload.TYPE, TogglePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TogglePayload.TYPE, (payload, ctx) -> toggle(ctx.player()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) ->
            d.register(Commands.literal("timestop").executes(ctx -> { toggle(ctx.getSource().getPlayerOrException()); return 1; })));
    }
}
