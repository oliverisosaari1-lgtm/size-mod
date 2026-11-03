package com.iikka.sizemod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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

    // 1 = tiny, 10 = normal, 20 = twice as big
    static double toScale(int size) {
        if (size <= 10) return size / 10.0;
        return 1.0 + (size - 10) / 10.0;
    }

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("size")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("players", EntityArgument.players())
                    .then(Commands.argument("size", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> {
                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                            int size = IntegerArgumentType.getInteger(ctx, "size");
                            double scale = toScale(size);
                            for (ServerPlayer player : players) {
                                AttributeInstance attr = player.getAttribute(Attributes.SCALE);
                                if (attr != null) attr.setBaseValue(scale);
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
