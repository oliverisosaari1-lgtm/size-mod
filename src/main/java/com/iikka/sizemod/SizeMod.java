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
    // Game scale at /size 0.1. Any smaller and the camera starts seeing through the ground.
    public static final double TINIEST_SCALE = 0.04;

    // Above 1 the number is the scale. Below 1 it shrinks fast (exponentially) down to
    // TINIEST_SCALE at 0.1, so 0.2 is already much smaller than a grass plant.
    public static double toScale(double size) {
        if (size >= 1.0) return size;
        double t = (1.0 - size) / (1.0 - MIN_SIZE); // 0 at size 1, 1 at size 0.1
        return Math.pow(TINIEST_SCALE, t);
    }

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
                                if (attr != null) attr.setBaseValue(toScale(size));
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
