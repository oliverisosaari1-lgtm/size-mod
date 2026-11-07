package com.iikka.move;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MoveMod implements ModInitializer {
    public static final String SEAT_TAG = "olkku_seat";
    public static final String LAY_NAME = "olkku_lay";

    private static final class Seat {
        final UUID player; final double groundY; final double wantY; int fixTicks = 6;
        Seat(UUID player, double groundY, double wantY) { this.player = player; this.groundY = groundY; this.wantY = wantY; }
    }

    private static final Map<Entity, Seat> seats = new HashMap<>();
    private static final List<Entity> strays = new ArrayList<>();
    private static final java.util.Set<UUID> crawling = new java.util.HashSet<>();

    public static boolean isCrawling(UUID id) { return crawling.contains(id); }

    /** Players who dashed or double jumped: no fall damage until they land. Value = ticks since. */
    private static final Map<UUID, Integer> noFall = new HashMap<>();

    private static void bar(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.AQUA), true);
    }

    private static boolean onOurSeat(ServerPlayer p) {
        return p.getVehicle() != null && seats.containsKey(p.getVehicle());
    }

    private static int seat(ServerPlayer p, boolean lay) {
        if (onOurSeat(p)) { p.stopRiding(); return 1; }
        if (p.isPassenger()) { bar(p, "Get off what you're riding first."); return 0; }
        if (!p.onGround()) { bar(p, "Stand on the ground first."); return 0; }
        ServerLevel level = (ServerLevel) p.level();
        double ground = p.getY();
        Display seat = lay ? new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level) : new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, level);
        seat.setPos(p.getX(), ground, p.getZ());
        seat.setCustomName(Component.literal(lay ? LAY_NAME : SEAT_TAG));
        seat.setCustomNameVisible(false);
        level.addFreshEntity(seat);
        // Sitting: legs rest on the ground. Lying: back rests on the ground.
        seats.put(seat, new Seat(p.getUUID(), ground, lay ? ground + 0.13 : ground - 0.55));
        p.startRiding(seat, true, true);
        bar(p, (lay ? "You lie down." : "You sit down.") + " Press Shift to get up.");
        return 1;
    }

    private static void tick(MinecraftServer server) {
        // Value: ticks since the move, or -1000 once you have been in the air.
        noFall.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null) return true;
            int v = en.getValue();
            boolean grounded = p.onGround() || p.isInWater();
            if (!grounded) v = -1000;
            else if (v < 0) return true;            // landed after being in the air
            else if (++v > 40) return true;         // dashed on the ground and never left it
            en.setValue(v);
            p.resetFallDistance();                  // the fall never adds up while protected
            return false;
        });        for (Entity e : strays) if (!seats.containsKey(e)) e.discard();
        strays.clear();
        Iterator<Map.Entry<Entity, Seat>> it = seats.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Entity seat = en.getKey();
            Seat s = en.getValue();
            Entity rider = seat.getFirstPassenger();
            if (seat.isRemoved() || rider == null) {
                // Stand up on top of the ground, not inside it.
                ServerPlayer p = server.getPlayerList().getPlayer(s.player);
                if (p != null && !p.isPassenger() && p.level() == seat.level() && p.distanceToSqr(seat) < 9)
                    p.teleportTo(seat.getX(), s.groundY, seat.getZ());
                seat.discard();
                it.remove();
                continue;
            }
            if (s.fixTicks > 0) {
                s.fixTicks--;
                double off = rider.getY() - seat.getY();
                seat.setPos(seat.getX(), s.wantY - off, seat.getZ());
                seat.positionRider(rider);
            }
        }
    }

    private static void broadcast(ServerPlayer p, byte kind) {
        AnimPayload anim = new AnimPayload(p.getId(), kind);
        for (ServerPlayer o : PlayerLookup.tracking(p)) if (o != p && ServerPlayNetworking.canSend(o, AnimPayload.TYPE)) ServerPlayNetworking.send(o, anim);
    }

    private static void onMove(ServerPlayer p, byte kind) {
        if (p.isSpectator()) return;
        ServerLevel level = (ServerLevel) p.level();
        switch (kind) {
            case Anim.STOP -> {
                crawling.remove(p.getUUID());
                if (onOurSeat(p)) p.stopRiding();
                broadcast(p, Anim.STOP);
            }
            case Anim.SIT -> { crawling.remove(p.getUUID()); seat(p, false); }
            case Anim.LAY -> { crawling.remove(p.getUUID()); seat(p, true); }
            case Anim.CRAWL -> {
                if (onOurSeat(p)) p.stopRiding();
                if (crawling.remove(p.getUUID())) bar(p, "You stand up.");
                else { crawling.add(p.getUUID()); bar(p, "You crawl. Press Jump to stand up."); }
            }
            case Anim.FLIP, Anim.DASH -> {
                if (p.isPassenger()) return;
                p.resetFallDistance();
                noFall.put(p.getUUID(), 0);
                if (kind == Anim.FLIP) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.6f, 1.6f);
                else level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.4f);
                broadcast(p, kind);
            }
            default -> {
                if (Anim.isGesture(kind)) broadcast(p, kind);
            }
        }
    }

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(MovePayload.TYPE, MovePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AnimPayload.TYPE, AnimPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MovePayload.TYPE, (payload, ctx) -> onMove(ctx.player(), payload.kind()));

        CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
            d.register(Commands.literal("sit").executes(ctx -> seat(ctx.getSource().getPlayerOrException(), false)));
            d.register(Commands.literal("lay").executes(ctx -> seat(ctx.getSource().getPlayerOrException(), true)));
        });
        ServerTickEvents.END_SERVER_TICK.register(MoveMod::tick);
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
            !(entity instanceof ServerPlayer p && noFall.containsKey(p.getUUID())
                && source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)));
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((h, srv) -> crawling.remove(h.player.getUUID()));
        // Old seats left in the world after a restart get cleaned up.
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e instanceof Display && e.getCustomName() != null && !seats.containsKey(e)
                && (SEAT_TAG.equals(e.getCustomName().getString()) || LAY_NAME.equals(e.getCustomName().getString()))) strays.add(e);
        });
    }
}
