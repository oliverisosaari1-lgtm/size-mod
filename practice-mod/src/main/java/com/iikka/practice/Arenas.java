package com.iikka.practice;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The PvP arenas, built high in the sky of the practice world. Rebuilt before every fight so blocks are fresh. */
public class Arenas {
    public static final List<String> NAMES = List.of("classic", "nether", "end", "desert", "ice", "sky");
    public static final int Y = 120, R = 16;

    public static String pretty(String a) { return Character.toUpperCase(a.charAt(0)) + a.substring(1); }

    public static BlockPos center(String a) {
        return new BlockPos(1000 + NAMES.indexOf(a) * 200, Y, 1000);
    }

    /** Where the two fighters start. */
    public static BlockPos spawn(String a, int side) {
        return center(a).offset(side == 0 ? -10 : 10, 1, 0);
    }

    public static void build(ServerLevel level, String a) {
        BlockPos c = center(a);
        Block floor, floor2, wall;
        switch (a) {
            case "nether" -> { floor = Blocks.NETHERRACK; floor2 = Blocks.BLACKSTONE; wall = Blocks.RED_STAINED_GLASS; }
            case "end" -> { floor = Blocks.END_STONE; floor2 = Blocks.END_STONE_BRICKS; wall = Blocks.PURPLE_STAINED_GLASS; }
            case "desert" -> { floor = Blocks.SANDSTONE; floor2 = Blocks.SMOOTH_SANDSTONE; wall = Blocks.ORANGE_STAINED_GLASS; }
            case "ice" -> { floor = Blocks.PACKED_ICE; floor2 = Blocks.SNOW_BLOCK; wall = Blocks.LIGHT_BLUE_STAINED_GLASS; }
            case "sky" -> { floor = Blocks.GRASS_BLOCK; floor2 = Blocks.MOSS_BLOCK; wall = Blocks.BARRIER; }
            default -> { floor = Blocks.STONE_BRICKS; floor2 = Blocks.POLISHED_ANDESITE; wall = Blocks.GLASS; }
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
            boolean edge = Math.abs(x) == R + 1 || Math.abs(z) == R + 1;
            for (int y = -4; y <= 20; y++) {
                BlockPos p = c.offset(x, y, z);
                BlockState s;
                if (y < 0) s = edge ? Blocks.AIR.defaultBlockState() : (y == -1 ? Blocks.DIRT.defaultBlockState() : air);
                else if (y == 0) s = (edge ? Blocks.BEDROCK : ((x + z) % 2 == 0 ? floor : floor2)).defaultBlockState();
                else if (edge) s = (y >= 20 ? Blocks.BARRIER : wall).defaultBlockState();
                else if (y == 20) s = Blocks.BARRIER.defaultBlockState();
                else s = air;
                if (y < -1 && !edge) s = Blocks.STONE.defaultBlockState();
                if (a.equals("sky") && y < 0 && !edge && Math.abs(x) + Math.abs(z) > R - y * 3) s = air;
                if (level.getBlockState(p) != s) level.setBlock(p, s, 2);
            }
        }
        // A few pillars for cover
        if (!a.equals("sky")) for (int[] o : new int[][]{{-5, -6}, {5, 6}, {5, -6}, {-5, 6}})
            for (int y = 1; y <= 3; y++) level.setBlock(c.offset(o[0], y, o[1]), floor2.defaultBlockState(), 2);
    }

    /** The lobby where you wait between fights. */
    public static BlockPos lobby() { return new BlockPos(0, Y, 0); }

    public static void buildLobby(ServerLevel level) {
        BlockPos c = lobby();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            level.setBlock(c.offset(x, 0, z), ((x + z) % 2 == 0 ? Blocks.RED_CONCRETE : Blocks.BLACK_CONCRETE).defaultBlockState(), 2);
            for (int y = 1; y <= 3; y++) if (Math.abs(x) == 6 || Math.abs(z) == 6) level.setBlock(c.offset(x, y, z), Blocks.BARRIER.defaultBlockState(), 2);
        }
        level.setBlock(c.offset(0, 0, 0), Blocks.SEA_LANTERN.defaultBlockState(), 2);
    }
}
