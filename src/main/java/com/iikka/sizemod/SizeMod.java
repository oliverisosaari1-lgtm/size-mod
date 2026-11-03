package com.iikka.sizemod;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Collection;

public class SizeMod implements ModInitializer {

    // 1 = normal size, 0.1 = smallest, 100 = biggest
    public static final double MIN_SIZE = 0.1;
    public static final double MAX_SIZE = 100.0;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("size")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("players", EntityArgument.players())
                    .then(Commands.argument("size", DoubleArgumentType.doubleArg(MIN_SIZE, MAX_SIZE))
                        .executes(ctx -> {
                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                            double size = DoubleArgumentType.getDouble(ctx, "size");
                            for (ServerPlayer player : players) {
                                AttributeInstance attr = player.getAttribute(Attributes.SCALE);
                                if (attr != null) attr.setBaseValue(size);
                            }
                            String who = players.size() == 1
                                ? players.iterator().next().getName().getString()
                                : players.size() + " players";
                            ctx.getSource().sendSuccess(
                                () -> Component.literal("Set size of " + who + " to " + size), true);
                            return players.size();
                        }))))
        );
    }
}
