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
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(LaserPayload.TYPE, LaserPayload.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(LaserPayload.TYPE, (payload, ctx) -> Toggles.laserHeld.put(ctx.player().getUUID(), ctx.player().level().getServer().getTickCount()));
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((pl, w, h, target, hit) ->
            pl instanceof ServerPlayer sp && h == net.minecraft.world.InteractionHand.MAIN_HAND && Powers.mount(sp, target) ? net.minecraft.world.InteractionResult.SUCCESS : net.minecraft.world.InteractionResult.PASS);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(RingPayload.TYPE, (payload, ctx) -> Rings.shoot(ctx.player()));
        ServerTickEvents.END_SERVER_TICK.register(Rings::tick);
        ServerTickEvents.END_SERVER_TICK.register(Wave::tick);
        ServerTickEvents.END_SERVER_TICK.register(Powers::tick);
        ServerTickEvents.END_SERVER_TICK.register(Toggles::tick);
        // With /slime on, mobs you hit bounce high
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((pl, w, h, target, hit) -> {
            if (pl instanceof ServerPlayer sp && Toggles.has("slime", sp)) { target.setDeltaMovement(target.getDeltaMovement().add(0, 1.2, 0)); target.hurtMarked = true; }
            return net.minecraft.world.InteractionResult.PASS;
        });
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("wave").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Wave.toggle(p);
                msg(p, on ? "Wave on: a sea of diamond blocks ripples under you and you float. Type /wave again to stop." : "Wave off.", on);
                return 1;
            }));
            d.register(Commands.literal("blackhole").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Powers.blackhole(p); msg(p, "Black hole: a black ball where you look sucks in mobs and blocks for 15 seconds.", true); return 1;
            }));
            d.register(Commands.literal("meteor").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Powers.meteor(p); msg(p, "Meteors: flaming meteors rain around you and explode for 10 seconds.", true); return 1;
            }));
            d.register(Commands.literal("dragonride").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Powers.dragonride(p);
                msg(p, on ? "Dragon ride on: right-click any mob to ride it. Look where you want to fly and hold W. Sneak to get off. Type /dragonride again to turn it off." : "Dragon ride off.", on);
                return 1;
            }));
            d.register(Commands.literal("lightning").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Toggles.toggle("lightning", p); msg(p, on ? "Lightning on: lightning strikes wherever you look. Type /lightning again to stop." : "Lightning off.", on); return 1;
            }));
            d.register(Commands.literal("magnet").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Toggles.toggle("magnet", p); msg(p, on ? "Magnet on: items and mobs near you fly to you. Type /magnet again to stop." : "Magnet off.", on); return 1;
            }));
            d.register(Commands.literal("laser").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Toggles.toggle("laser", p); msg(p, on ? "Laser on: hold B to shoot a laser from your eyes. It burns through blocks and mobs. Type /laser again to turn it off." : "Laser off.", on); return 1;
            }));
            d.register(Commands.literal("slime").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Toggles.toggle("slime", p); msg(p, on ? "Slime on: blocks you touch turn into slime and mobs you hit bounce up. Type /slime again to stop." : "Slime off.", on); return 1;
            }));
            d.register(Commands.literal("swords").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                boolean on = Toggles.toggle("swords", p); msg(p, on ? "Swords on: 6 swords spin around you and hit mobs that get close. Type /swords again to stop." : "Swords off.", on); return 1;
            }));
            d.register(Commands.literal("volcano").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Toggles.volcano(p); msg(p, "Volcano: a volcano rises where you look and shoots lava for 15 seconds.", true); return 1;
            }));
            d.register(Commands.literal("ring").executes(ctx -> { Rings.openMenu(ctx.getSource().getPlayerOrException()); return 1; }));
            d.register(Commands.literal("aura").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                if (auras.containsKey(p.getUUID())) { removeAura(p.getUUID()); msg(p, "Aura off.", false); }
                else { Aura a = new Aura(); auras.put(p.getUUID(), a); buildGlass(p, a); msg(p, "Aura on: a glass sphere follows you and breaks every block inside it. Sneak to dig down. Type /aura again to stop.", true); }
                return 1;
            }));
            d.register(Commands.literal("supertouch").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                if (superTouch.remove(p.getUUID())) msg(p, "Super touch off.", false);
                else { superTouch.add(p.getUUID()); msg(p, "Super touch on: break a block and all the same blocks touching it break too. Type /supertouch again to stop.", true); }
                return 1;
            }));
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayer p && superTouch.contains(p.getUUID())) veinMine((ServerLevel) world, p, pos, state);
        });
        ServerTickEvents.END_SERVER_TICK.register(AuraMod::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { removeAura(h.player.getUUID()); Wave.remove(h.player.getUUID()); Toggles.leave(h.player.getUUID()); superTouch.remove(h.player.getUUID()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { Rings.clear(); Wave.clear(); Powers.clear(); Toggles.clear(); for (UUID id : new ArrayList<>(auras.keySet())) removeAura(id); });
        // Leftover glass from a crash is cleaned up
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e.getTags().contains(Rings.TAG) && e.tickCount > 100) e.discard();
            if (e.getTags().contains(Powers.TAG) && !Toggles.ours(e) && e.tickCount > 400) e.discard();
            if (e.getTags().contains(Wave.TAG) && !Wave.ours(e)) e.discard();
            if (e.getTags().contains(TAG) && auras.values().stream().noneMatch(a -> a.glass.contains(e))) e.discard();
        });
    }
}
