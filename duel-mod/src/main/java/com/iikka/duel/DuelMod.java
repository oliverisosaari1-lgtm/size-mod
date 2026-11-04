package com.iikka.duel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DuelMod implements ModInitializer {
    static MinecraftServer server;
    static SaveData DATA = new SaveData();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** What we keep on disk: people's own stuff while they're in a duel, so nothing is lost if the server stops. */
    public static class SaveData {
        public boolean arenaBuilt = false;
        public Map<String, Saved> saved = new HashMap<>();
    }
    public static class Saved {
        public List<String> items = new ArrayList<>();
        public String dimension;
        public double x, y, z;
        public float yaw, pitch;
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("duelmod.json");
    }

    static void save() {
        try { Files.writeString(file(), GSON.toJson(DATA)); } catch (Exception ignored) { }
    }

    static void say(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal(text).withStyle(color));
    }

    static void teleport(ServerPlayer p, ServerLevel level, double x, double y, double z, float yaw, float pitch) {
        p.teleportTo(level, x, y, z, Set.of(), yaw, pitch, true);
    }

    static ServerLevel levelByName(String name) {
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().toString().equals(name)) return l;
        return null;
    }

    static String saveItem(ItemStack stack) {
        if (stack.isEmpty()) return "{}";
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        return ItemStack.CODEC.encodeStart(ops, stack).getOrThrow().toString();
    }

    static ItemStack loadItem(String json) {
        try {
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            return ItemStack.CODEC.parse(ops, JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            try { if (Files.exists(file())) DATA = GSON.fromJson(Files.readString(file()), SaveData.class); } catch (Exception ignored) { }
            if (DATA == null) DATA = new SaveData();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> s.execute(() -> {
            if (!Duels.inDuel(handler.player)) Duels.restore(handler.player); // left during a duel last time
        }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            if (Duels.inDuel(handler.player)) Duels.lose(handler.player.getUUID()); // leaving = losing
        });

        // Dying in a duel: no real death, you just lose and get your stuff back. Totems still work.
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer p) || !Duels.inDuel(p)) return true;
            if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return true;
            p.setHealth(p.getMaxHealth());
            Duels.lose(p.getUUID());
            return false;
        });

        // No breaking or placing blocks in the arena (except in creative)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) ->
            player.isCreative() || world != server.overworld() || !Arena.inside(pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClientSide() && !player.isCreative() && server != null && world == server.overworld() && Arena.inside(hit.getBlockPos())
                && (player.getItemInHand(hand).getItem() instanceof BlockItem || player.getItemInHand(hand).getItem() instanceof BucketItem))
                return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });

        CommandRegistrationCallback.EVENT.register((d, registryAccess, env) -> {
            d.register(Commands.literal("duel")
                .then(Commands.literal("accept").executes(ctx -> { Duels.accept(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.literal("deny").executes(ctx -> { Duels.deny(ctx.getSource().getPlayerOrException()); return 1; }))
                .then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    Duels.challenge(ctx.getSource().getPlayerOrException(), EntityArgument.getPlayer(ctx, "player"));
                    return 1;
                })));
        });
    }
}
