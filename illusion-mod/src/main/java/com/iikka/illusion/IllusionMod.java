package com.iikka.illusion;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class IllusionMod implements ModInitializer {
    private static final Map<UUID, Illusion> chosen = new HashMap<>();
    private static MinecraftServer server;

    /** An arrow in the air with an illusion on it. */
    private static class Flying {
        final Entity arrow; final Illusion illusion; int age = 0; Vec3 last; int still = 0;
        Flying(Entity a, Illusion i) { arrow = a; illusion = i; last = a.position(); }
    }
    private static final List<Flying> flying = new ArrayList<>();

    public static Illusion get(ServerPlayer p) { return chosen.get(p.getUUID()); }

    public static void set(ServerPlayer p, Illusion i) {
        if (i == null) chosen.remove(p.getUUID()); else chosen.put(p.getUUID(), i);
        save();
    }

    private static Path file() { return server.getWorldPath(LevelResource.ROOT).resolve("illusions.txt"); }

    private static void save() {
        if (server == null) return;
        StringBuilder b = new StringBuilder();
        chosen.forEach((id, il) -> b.append(id).append('=').append(il.name()).append('\n'));
        try { Files.writeString(file(), b.toString()); } catch (Exception ignored) { }
    }

    private static void load() {
        chosen.clear();
        try {
            if (!Files.exists(file())) return;
            for (String line : Files.readAllLines(file())) {
                String[] kv = line.split("=");
                if (kv.length == 2) chosen.put(UUID.fromString(kv[0]), Illusion.valueOf(kv[1]));
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(); });

        CommandRegistrationCallback.EVENT.register((d, reg, env) ->
            d.register(Commands.literal("illusion").executes(ctx -> { IllusionMenu.open(ctx.getSource().getPlayerOrException()); return 1; })));

        // A new arrow (or trident) shot by a player with an illusion
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            String id = EntityType.getKey(entity.getType()).getPath();
            boolean arrow = id.equals("arrow") || id.equals("spectral_arrow") || id.equals("trident");
            if (arrow && entity instanceof TraceableEntity t && t.getOwner() instanceof ServerPlayer p && entity.tickCount < 2) {
                Illusion il = get(p);
                if (il != null) flying.add(new Flying(entity, il));
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            for (Iterator<Flying> it = flying.iterator(); it.hasNext(); ) {
                Flying f = it.next();
                ServerLevel level = (ServerLevel) f.arrow.level();
                Vec3 now = f.arrow.position();
                boolean gone = f.arrow.isRemoved();
                if (!gone && now.distanceToSqr(f.last) < 0.0004) f.still++; else f.still = 0;
                if (gone || f.still >= 1 || f.age > 400) {
                    f.illusion.hit(level, gone ? f.last : now);
                    it.remove();
                    continue;
                }
                // Fill the gap between last tick and now so the trail is smooth
                for (int i = 0; i < 3; i++) f.illusion.trail(level, f.last.lerp(now, i / 3.0), f.age);
                f.last = now;
                f.age++;
            }
        });
    }
}
