package com.iikka.aura;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * /aura: a glass sphere around you that moves with you and breaks every block inside it.
 * /supertouch: breaking a block also breaks all connected blocks of the same kind.
 */
public class AuraMod implements ModInitializer {
    private static final String TAG = "aura_glass";
    private static final double R = 4.5;
    private static final List<BlockPos> SHELL = new ArrayList<>(), INSIDE = new ArrayList<>();
    static {
        int r = (int) Math.ceil(R);
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d <= R) INSIDE.add(new BlockPos(x, y, z));
            if (d <= R && d > R - 1) SHELL.add(new BlockPos(x, y, z));
        }
    }

    private static class Aura { final List<Display.BlockDisplay> glass = new ArrayList<>(); BlockPos at; ServerLevel level; }
    private static final Map<UUID, Aura> auras = new HashMap<>();
    private static final Set<UUID> superTouch = new HashSet<>();

    private static void msg(ServerPlayer p, String text, boolean on) {
        p.displayClientMessage(Component.literal(text).withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY), false);
    }

    private static void removeAura(UUID id) {
        Aura a = auras.remove(id);
        if (a != null) for (Entity e : a.glass) e.discard();
    }

    private static BlockPos center(ServerPlayer p) {
        return BlockPos.containing(p.getX(), p.getY() + 1, p.getZ());
    }

    private static void buildGlass(ServerPlayer p, Aura a) {
        for (Entity e : a.glass) e.discard();
        a.glass.clear();
        a.level = (ServerLevel) p.level();
        a.at = center(p);
        for (BlockPos off : SHELL) {
            Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(a.level, EntitySpawnReason.COMMAND);
            if (d == null) continue;
            d.setBlockState(Blocks.GLASS.defaultBlockState());
            d.setPosRotInterpolationDuration(2);
            d.addTag(TAG);
            BlockPos at = a.at.offset(off);
            d.setPos(at.getX(), at.getY(), at.getZ());
            a.glass.add(d);
            a.level.addFreshEntity(d);
        }
    }

    private static boolean breakable(BlockState s) {
        return !s.isAir() && s.getDestroySpeed(null, BlockPos.ZERO) >= 0 // not bedrock/barrier
            && !s.is(Blocks.END_PORTAL_FRAME);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Aura a = auras.get(p.getUUID());
            if (a == null) continue;
            if (a.level != p.level() || a.glass.isEmpty() || a.glass.get(0).isRemoved()) buildGlass(p, a);
            BlockPos c = center(p);
            if (!c.equals(a.at)) {
                a.at = c;
                for (int i = 0; i < a.glass.size() && i < SHELL.size(); i++) {
                    BlockPos at = c.offset(SHELL.get(i));
                    a.glass.get(i).teleportTo(at.getX(), at.getY(), at.getZ());
                }
            }
            // Break everything inside the sphere. Blocks under your feet only when you sneak (so you don't fall forever).
            ServerLevel level = (ServerLevel) p.level();
            int feet = BlockPos.containing(p.position()).getY();
            for (BlockPos off : INSIDE) {
                BlockPos pos = c.offset(off);
                if (pos.getY() < feet && !p.isShiftKeyDown()) continue;
                BlockState s = level.getBlockState(pos);
                if (breakable(s)) level.destroyBlock(pos, false, p);
                else if (!s.getFluidState().isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void veinMine(ServerLevel level, ServerPlayer p, BlockPos start, BlockState type) {
        Deque<BlockPos> todo = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        todo.add(start);
        seen.add(start);
        int broken = 0;
        while (!todo.isEmpty() && broken < 1024) {
            BlockPos pos = todo.poll();
            for (Direction dir : Direction.values()) {
                BlockPos n = pos.relative(dir);
                if (seen.add(n) && level.getBlockState(n).is(type.getBlock())) {
                    level.destroyBlock(n, !p.isCreative(), p);
                    broken++;
                    todo.add(n);
                }
            }
        }
    }

    @Override
    public void onInitialize() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(RingPayload.TYPE, RingPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(RingPayload.TYPE, (payload, ctx) -> Rings.shoot(ctx.player()));
        ServerTickEvents.END_SERVER_TICK.register(Rings::tick);
        ServerTickEvents.END_SERVER_TICK.register(Wave::tick);
        ServerTickEvents.END_SERVER_TICK.register(Powers::tick);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("wave").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Wave.toggle(p);
                msg(p, on ? "Wave on! A sea of diamond blocks ripples under you." : "Wave off.", on);
                return 1;
            }));
            d.register(Commands.literal("blackhole").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Powers.blackhole(p); msg(p, "Black hole! It sucks everything in for 15 seconds.", true); return 1;
            }));
            d.register(Commands.literal("meteor").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Powers.meteor(p); msg(p, "Meteors incoming for 10 seconds!", true); return 1;
            }));
            d.register(Commands.literal("dragonride").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                String r = Powers.dragonride(p);
                if (r == null) msg(p, "Look at a mob close to you first, then type /dragonride.", false); else msg(p, r, true);
                return 1;
            }));
            d.register(Commands.literal("ring").executes(ctx -> { Rings.openMenu(ctx.getSource().getPlayerOrException()); return 1; }));
            d.register(Commands.literal("aura").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                if (auras.containsKey(p.getUUID())) { removeAura(p.getUUID()); msg(p, "Aura off.", false); }
                else { Aura a = new Aura(); auras.put(p.getUUID(), a); buildGlass(p, a); msg(p, "Aura on! Everything inside the glass breaks. Sneak to dig down.", true); }
                return 1;
            }));
            d.register(Commands.literal("supertouch").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                if (superTouch.remove(p.getUUID())) msg(p, "Super touch off.", false);
                else { superTouch.add(p.getUUID()); msg(p, "Super touch on! Break a block and all the same blocks touching it break too.", true); }
                return 1;
            }));
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayer p && superTouch.contains(p.getUUID())) veinMine((ServerLevel) world, p, pos, state);
        });
        ServerTickEvents.END_SERVER_TICK.register(AuraMod::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { removeAura(h.player.getUUID()); Wave.remove(h.player.getUUID()); superTouch.remove(h.player.getUUID()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { Rings.clear(); Wave.clear(); Powers.clear(); for (UUID id : new ArrayList<>(auras.keySet())) removeAura(id); });
        // Leftover glass from a crash is cleaned up
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e.getTags().contains(Rings.TAG) && e.tickCount > 100) e.discard();
            if (e.getTags().contains(Powers.TAG) && e.tickCount > 400) e.discard();
            if (e.getTags().contains(Wave.TAG) && !Wave.ours(e)) e.discard();
            if (e.getTags().contains(TAG) && auras.values().stream().noneMatch(a -> a.glass.contains(e))) e.discard();
        });
    }
}
