package com.iikka.walk;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Players live in an empty world (walkgen:world). The normal overworld is made in the background,
 * and every time you walk, the blocks around you get copied from it into your empty world.
 */
public class WalkMod implements ModInitializer {
    public static final ResourceKey<Level> WORLD = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("walkgen", "world"));
    private static final int RADIUS = 3;
    private static final Set<Long> revealed = new HashSet<>();
    private static final Map<UUID, Long> lastColumn = new HashMap<>();
    private static MinecraftServer server;
    private static boolean dirty = false;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; load(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            ServerLevel walk = s.getLevel(WORLD);
            ServerLevel real = s.overworld();
            if (walk == null) return;
            for (ServerPlayer p : new ArrayList<>(s.getPlayerList().getPlayers())) {
                if (p.level() == real) {
                    // Anyone in the normal overworld (new players, respawns, back from the Nether) goes to the empty world
                    reveal(real, walk, p.getBlockX(), p.getBlockZ());
                    p.teleportTo(walk, p.getX(), p.getY(), p.getZ(), Set.of(), p.getYRot(), p.getXRot(), true);
                    continue;
                }
                if (p.level() != walk) continue;
                long col = BlockPos.asLong(p.getBlockX(), 0, p.getBlockZ());
                if (Objects.equals(lastColumn.get(p.getUUID()), col)) continue;
                lastColumn.put(p.getUUID(), col);
                reveal(real, walk, p.getBlockX(), p.getBlockZ());
            }
            if (dirty && s.getTickCount() % 1200 == 0) save();
        });
    }

    private static void reveal(ServerLevel real, ServerLevel walk, int cx, int cz) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                if (dx * dx + dz * dz > RADIUS * RADIUS + 1) continue;
                int x = cx + dx, z = cz + dz;
                if (!revealed.add(BlockPos.asLong(x, 0, z))) continue;
                dirty = true;
                copyColumn(real, walk, x, z);
            }
        }
    }

    private static void copyColumn(ServerLevel real, ServerLevel walk, int x, int z) {
        int top = real.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); // this makes the real world there if it isn't yet
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = real.getMinY(); y <= top; y++) {
            pos.set(x, y, z);
            BlockState s = real.getBlockState(pos);
            if (s.isAir()) continue;
            if (!walk.getBlockState(pos).isAir()) continue; // don't overwrite things players built
            walk.setBlock(pos, s, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static Path file() {
        return server.getWorldPath(LevelResource.ROOT).resolve("walkgen_revealed.bin");
    }

    private static void load() {
        revealed.clear();
        try {
            if (!Files.exists(file())) return;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file())))) {
                int n = in.readInt();
                for (int i = 0; i < n; i++) revealed.add(in.readLong());
            }
        } catch (Exception ignored) { }
    }

    private static void save() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(file())))) {
            out.writeInt(revealed.size());
            for (long l : revealed) out.writeLong(l);
            dirty = false;
        } catch (Exception ignored) { }
    }
}
