package com.iikka.smp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The lobby: a round floating platform high in the sky, built by the mod the first time someone types /lobby. */
public class Lobby {
    public static final int X = 10_000, Y = 250, Z = 10_000;
    private static final int R = 20; // radius

    public static boolean inside(BlockPos p) {
        int dx = p.getX() - X, dz = p.getZ() - Z;
        return dx * dx + dz * dz <= (R + 2) * (R + 2) && p.getY() >= Y - 3 && p.getY() <= Y + 20;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(X + x, Y + y, Z + z), s, 3);
    }

    public static void build(ServerLevel level) {
        BlockState floor = Blocks.SMOOTH_QUARTZ.defaultBlockState();
        BlockState ring = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        BlockState dark = Blocks.DARK_PRISMARINE.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState base = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState glass = Blocks.GLASS.defaultBlockState();
        BlockState pillar = Blocks.QUARTZ_PILLAR.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();

        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > R + 0.5) continue;
                set(level, x, -1, z, base);
                BlockState top = floor;
                if (d > R - 3.5) top = ring;                      // blue ring at the edge
                else if (Math.abs(d - 10) < 0.7) top = dark;      // inner circle
                else if (x % 6 == 0 && z % 6 == 0) top = light;   // lights in the floor
                set(level, x, 0, z, top);
                for (int y = 1; y <= 12; y++) set(level, x, y, z, Blocks.AIR.defaultBlockState());
                if (d > R - 0.5) for (int y = 1; y <= 3; y++) set(level, x, y, z, glass); // glass wall
            }
        }
        // Fountain in the middle
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
            set(level, x, 0, z, edge ? dark : water);
            if (edge) set(level, x, 1, z, Blocks.QUARTZ_SLAB.defaultBlockState());
        }
        set(level, 0, 0, 0, light);
        set(level, 0, 1, 0, pillar);
        set(level, 0, 2, 0, pillar);
        set(level, 0, 3, 0, light);
        // Four towers with lights on top
        int[][] towers = {{12, 12}, {-12, 12}, {12, -12}, {-12, -12}};
        for (int[] t : towers) {
            for (int y = 1; y <= 5; y++) set(level, t[0], y, t[1], pillar);
            set(level, t[0], 6, t[1], light);
        }
    }
}
