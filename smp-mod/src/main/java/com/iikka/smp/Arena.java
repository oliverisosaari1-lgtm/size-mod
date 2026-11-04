package com.iikka.smp;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The duel arena: a big square floating platform with tall glass walls. Built the first time someone duels. */
public class Arena {
    public static final int X = 20_000, Y = 200, Z = 20_000;
    private static final int R = 20;      // half the width
    private static final int WALL = 40;   // tall walls so maces and wind charges have room

    public static boolean inside(BlockPos p) {
        return Math.abs(p.getX() - X) <= R + 1 && Math.abs(p.getZ() - Z) <= R + 1 && p.getY() >= Y - 3 && p.getY() <= Y + WALL + 2;
    }

    private static void set(ServerLevel level, int x, int y, int z, BlockState s) {
        level.setBlock(new BlockPos(X + x, Y + y, Z + z), s, 3);
    }

    public static void build(ServerLevel level) {
        BlockState floor = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState line = Blocks.RED_CONCRETE.defaultBlockState();
        BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
        BlockState base = Blocks.OBSIDIAN.defaultBlockState();
        BlockState wall = Blocks.TINTED_GLASS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                boolean edge = Math.abs(x) == R || Math.abs(z) == R;
                set(level, x, -1, z, base);
                BlockState top = floor;
                if (x == 0 || Math.abs(x) == R - 1 || Math.abs(z) == R - 1) top = line;
                else if (x % 5 == 0 && z % 5 == 0) top = light;
                set(level, x, 0, z, top);
                for (int y = 1; y <= WALL; y++) set(level, x, y, z, edge ? wall : air);
            }
        }
    }
}
