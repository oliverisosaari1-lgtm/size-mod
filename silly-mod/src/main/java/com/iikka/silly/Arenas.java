package com.iikka.silly;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The battle maps. Each one is built once, high in the sky, far away from your normal world. */
public final class Arenas {
    private Arenas() { }

    public static final int HALF = 56;      // maps are 113 x 113 blocks
    public static final int Y = 220;        // floor height
    public static final int SPACING = 400;

    public interface Builder { void build(B b); }
    public record Arena(String id, String name, String about, Builder builder) { }

    public static final List<Arena> ALL = new ArrayList<>();
    static {
        ALL.add(new Arena("colosseum", "Colosseum", "A giant Roman fighting ring", Arenas::colosseum));
        ALL.add(new Arena("school", "School", "Classrooms, a gym and the cafeteria", Arenas::school));
        ALL.add(new Arena("area51", "Area 51", "Secret base with a crashed UFO", Arenas::area51));
        ALL.add(new Arena("kitchen", "Giant Kitchen", "You are tiny. Everything is huge", Arenas::kitchen));
        ALL.add(new Arena("bouncy", "Bouncy Castle", "Boing! Slime jump pads everywhere", Arenas::bouncy));
        ALL.add(new Arena("supermarket", "Supermarket", "Fight in the aisles. Everything on sale", Arenas::supermarket));
        ALL.add(new Arena("moon", "Moon Base", "Low gravity! Big jumps", Arenas::moon));
        ALL.add(new Arena("pirate", "Pirate Ship", "A huge ship on the sea", Arenas::pirate));
        ALL.add(new Arena("bathroom", "Giant Bathroom", "A huge toilet, bathtub and rubber duck", Arenas::bathroom));
    }

    public static Arena get(String id) {
        for (Arena a : ALL) if (a.id.equalsIgnoreCase(id)) return a;
        return null;
    }

    public static int index(Arena a) { return ALL.indexOf(a); }
    public static BlockPos center(Arena a) { return new BlockPos(20000 + index(a) * SPACING, Y, 20000); }

    public static void build(ServerLevel l, Arena a) {
        B b = new B(l, center(a));
        // base: a floor of stone under everything and invisible walls around the edge
        b.fill(-HALF - 1, -1, -HALF - 1, HALF + 1, -1, HALF + 1, b(Blocks.SMOOTH_STONE));
        a.builder.build(b);
        for (int i = -HALF - 1; i <= HALF + 1; i++) {
            b.fill(i, 0, -HALF - 1, i, 50, -HALF - 1, b(Blocks.BARRIER));
            b.fill(i, 0, HALF + 1, i, 50, HALF + 1, b(Blocks.BARRIER));
            b.fill(-HALF - 1, 0, i, -HALF - 1, 50, i, b(Blocks.BARRIER));
            b.fill(HALF + 1, 0, i, HALF + 1, 50, i, b(Blocks.BARRIER));
        }
    }

    /** A random safe place to stand in the map. */
    public static Vec3 spawnSpot(ServerLevel l, Arena a, Random r) {
        BlockPos c = center(a);
        for (int tries = 0; tries < 200; tries++) {
            int x = c.getX() + r.nextInt(HALF * 2 - 8) - HALF + 4, z = c.getZ() + r.nextInt(HALF * 2 - 8) - HALF + 4;
            for (int y = c.getY() + 30; y >= c.getY(); y--) {
                BlockPos p = new BlockPos(x, y, z);
                BlockState below = l.getBlockState(p.below());
                if (!below.isAir() && below.getFluidState().isEmpty() && !below.is(Blocks.BARRIER) && !below.is(Blocks.COBWEB)
                    && l.getBlockState(p).isAir() && l.getBlockState(p.above()).isAir()) {
                    return new Vec3(x + 0.5, y, z + 0.5);
                }
                if (!l.getBlockState(p).isAir() && !l.getBlockState(p).getFluidState().isEmpty()) break;
            }
        }
        return new Vec3(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
    }

    public static boolean inside(Arena a, Vec3 p) {
        BlockPos c = center(a);
        return Math.abs(p.x - c.getX()) <= HALF + 2 && Math.abs(p.z - c.getZ()) <= HALF + 2 && p.y > c.getY() - 10 && p.y < c.getY() + 60;
    }

    static BlockState b(Block x) { return x.defaultBlockState(); }

    // ======================= the builder =======================

    public static final class B {
        final ServerLevel l;
        final int ox, oy, oz;
        final Random r = new Random(51);

        B(ServerLevel l, BlockPos o) { this.l = l; ox = o.getX(); oy = o.getY(); oz = o.getZ(); }

        void set(int x, int y, int z, BlockState s) { l.setBlock(new BlockPos(ox + x, oy + y, oz + z), s, 2); }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                    for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
        }

        /** Four walls (no roof, no floor). */
        void walls(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
            fill(x1, y1, z1, x2, y2, z1, s);
            fill(x1, y1, z2, x2, y2, z2, s);
            fill(x1, y1, z1, x1, y2, z2, s);
            fill(x2, y1, z1, x2, y2, z2, s);
        }

        void floor(BlockState s) { fill(-HALF, 0, -HALF, HALF, 0, HALF, s); }

        void checker(int x1, int z1, int x2, int z2, int y, int size, BlockState a, BlockState c) {
            for (int x = x1; x <= x2; x++)
                for (int z = z1; z <= z2; z++)
                    set(x, y, z, (Math.floorDiv(x, size) + Math.floorDiv(z, size)) % 2 == 0 ? a : c);
        }

        void cyl(int cx, int cz, double rad, int y1, int y2, BlockState s, boolean hollow) {
            int R = (int) Math.ceil(rad);
            for (int x = -R; x <= R; x++)
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= rad && (!hollow || d > rad - 1.2)) fill(cx + x, y1, cz + z, cx + x, y2, cz + z, s);
                }
        }

        void ball(int cx, int cy, int cz, double rad, BlockState s, boolean hollow, boolean topOnly) {
            int R = (int) Math.ceil(rad);
            for (int x = -R; x <= R; x++)
                for (int y = topOnly ? 0 : -R; y <= R; y++)
                    for (int z = -R; z <= R; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z);
                        if (d <= rad && (!hollow || d > rad - 1.2)) set(cx + x, cy + y, cz + z, s);
                    }
        }

        /** Big letters on a wall. Letters go along (dx, dz) and up. Each letter is 3 wide, 5 tall, times scale. */
        void text(String t, int x, int y, int z, int dx, int dz, int scale, BlockState s) {
            int col = 0;
            for (char ch : t.toUpperCase().toCharArray()) {
                String[] g = Font.glyph(ch);
                for (int row = 0; row < 5; row++)
                    for (int c = 0; c < 3; c++)
                        if (g[row].charAt(c) == '#')
                            for (int a = 0; a < scale; a++)
                                for (int bb = 0; bb < scale; bb++) {
                                    int along = (col + c) * scale + a;
                                    set(x + dx * along, y + (4 - row) * scale + bb, z + dz * along, s);
                                }
                col += 4;
            }
        }

        static int textWidth(String t, int scale) { return (t.length() * 4 - 1) * scale; }

        /** Text centred on a point. */
        void textC(String t, int cx, int y, int cz, int dx, int dz, int scale, BlockState s) {
            int w = textWidth(t, scale);
            text(t, cx - dx * w / 2, y, cz - dz * w / 2, dx, dz, scale, s);
        }

        int rnd(int a, int b) { return a + r.nextInt(b - a + 1); }
    }

    // ======================= COLOSSEUM =======================

    static void colosseum(B b) {
        b.floor(b(Blocks.SANDSTONE));
        for (int x = -HALF; x <= HALF; x++)
            for (int z = -HALF; z <= HALF; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 47) b.set(x, 0, z, b(((int) d) % 6 == 0 ? Blocks.SMOOTH_SANDSTONE : (b.r.nextInt(5) == 0 ? Blocks.SUSPICIOUS_SAND : Blocks.SAND)));
                if (d < 47 && b.r.nextInt(3) == 0) b.set(x, 0, z, b(Blocks.SAND));
            }
        // seating: rising rings outside the wall
        for (int x = -HALF; x <= HALF; x++)
            for (int z = -HALF; z <= HALF; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d >= 50) {
                    int h = Math.min(8, 2 + (int) ((d - 50) * 1.2));
                    b.fill(x, 1, z, x, h, z, b(((int) d) % 2 == 0 ? Blocks.STONE_BRICKS : Blocks.SMOOTH_SANDSTONE));
                }
            }
        // the big round wall with arches
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            for (double rr = 47; rr <= 49.5; rr += 0.5) {
                int x = (int) Math.round(Math.cos(a) * rr), z = (int) Math.round(Math.sin(a) * rr);
                boolean arch = (i % 30) < 6;
                for (int y = 1; y <= 10; y++) {
                    if (arch && y <= 4) b.set(x, y, z, b(Blocks.AIR));
                    else b.set(x, y, z, b(y == 10 ? Blocks.CHISELED_STONE_BRICKS : y % 4 == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS));
                }
            }
        }
        // pillars with fire on top
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = (int) (Math.cos(a) * 30), z = (int) (Math.sin(a) * 30);
            b.fill(x, 1, z, x + 1, 7, z + 1, b(Blocks.QUARTZ_PILLAR));
            b.fill(x, 8, z, x + 1, 8, z + 1, b(Blocks.CHISELED_QUARTZ_BLOCK));
            b.set(x, 9, z, b(Blocks.NETHERRACK)); b.set(x, 10, z, b(Blocks.FIRE));
            b.set(x + 1, 9, z + 1, b(Blocks.NETHERRACK)); b.set(x + 1, 10, z + 1, b(Blocks.FIRE));
        }
        // broken column cover
        for (int k = 0; k < 14; k++) {
            int x = b.rnd(-38, 38), z = b.rnd(-38, 38);
            if (Math.abs(x) < 8 && Math.abs(z) < 8) continue;
            b.fill(x, 1, z, x, b.rnd(1, 4), z, b(Blocks.CRACKED_STONE_BRICKS));
            b.set(x + 1, 1, z, b(Blocks.STONE_BRICK_SLAB));
        }
        // centre stage
        b.fill(-5, 1, -5, 5, 1, 5, b(Blocks.SMOOTH_STONE));
        b.fill(-3, 2, -3, 3, 2, 3, b(Blocks.POLISHED_ANDESITE));
        b.fill(-1, 3, -1, 1, 3, 1, b(Blocks.GOLD_BLOCK));
        // the emperor's box with the name
        b.fill(-8, 9, -55, 8, 9, -51, b(Blocks.CONCRETE.get(DyeColor.RED)));
        b.textC("COLOSSEUM", 0, 12, -52, 1, 0, 1, b(Blocks.GOLD_BLOCK));
        for (int x = -50; x <= 50; x += 10) { b.set(x, 1, -44, b(Blocks.LANTERN)); b.set(x, 1, 44, b(Blocks.LANTERN)); }
    }

    // ======================= SCHOOL =======================

    static void school(B b) {
        b.floor(b(Blocks.GRASS_BLOCK));
        int s = 48; // the building
        b.fill(-s, 0, -s, s, 0, s, b(Blocks.POLISHED_ANDESITE));
        // corridors floor
        b.checker(-s, -3, s, 3, 0, 1, b(Blocks.CONCRETE.get(DyeColor.WHITE)), b(Blocks.CONCRETE.get(DyeColor.LIGHT_GRAY)));
        b.checker(-3, -s, 3, s, 0, 1, b(Blocks.CONCRETE.get(DyeColor.WHITE)), b(Blocks.CONCRETE.get(DyeColor.LIGHT_GRAY)));
        BlockState wall = b(Blocks.CONCRETE.get(DyeColor.WHITE)), trim = b(Blocks.CONCRETE.get(DyeColor.LIGHT_BLUE));
        b.walls(-s, 1, -s, s, 6, s, wall);
        b.fill(-s, 1, -s, s, 1, -s, trim); b.fill(-s, 1, s, s, 1, s, trim); b.fill(-s, 1, -s, -s, 1, s, trim); b.fill(s, 1, -s, s, 1, s, trim);
        // main doors
        b.fill(-2, 1, s, 2, 4, s, b(Blocks.AIR));
        b.fill(-2, 1, -s, 2, 4, -s, b(Blocks.AIR));
        b.fill(s, 1, -2, s, 4, 2, b(Blocks.AIR));
        b.fill(-s, 1, -2, -s, 4, 2, b(Blocks.AIR));
        // corridor walls
        for (int sign : new int[] { -1, 1 }) {
            b.fill(-s, 1, 4 * sign, -4, 5, 4 * sign, wall); b.fill(4, 1, 4 * sign, s, 5, 4 * sign, wall);
            b.fill(4 * sign, 1, -s, 4 * sign, 5, -4, wall); b.fill(4 * sign, 1, 4, 4 * sign, 5, s, wall);
        }
        // lockers along the corridor
        for (int x = -s + 2; x <= -6; x += 2) { b.fill(x, 1, 3, x, 2, 3, b(Blocks.CONCRETE.get(DyeColor.BLUE))); b.fill(-x, 1, -3, -x, 2, -3, b(Blocks.CONCRETE.get(DyeColor.RED))); }
        // classrooms: north-west and south-west, 4 rooms each
        classrooms(b, -s, -s, -4, -4);
        classrooms(b, -s, 4, -4, s);
        // gym: north-east
        gym(b, 4, -s, s, -4);
        // cafeteria: south-east
        cafeteria(b, 4, 4, s, s);
        // name above the front door
        b.textC("SCHOOL", 0, 8, s, 1, 0, 2, b(Blocks.CONCRETE.get(DyeColor.ORANGE)));
        b.fill(-14, 7, s, 14, 7, s, wall);
        // flag pole and playground outside
        b.fill(10, 1, s + 4, 10, 14, s + 4, b(Blocks.IRON_BARS));
        b.fill(11, 11, s + 4, 15, 13, s + 4, b(Blocks.WOOL.get(DyeColor.BLUE)));
        b.fill(-20, 1, s + 3, -12, 1, s + 6, b(Blocks.SAND));
        b.fill(-18, 1, s + 4, -18, 4, s + 4, b(Blocks.OAK_FENCE)); b.fill(-14, 1, s + 4, -14, 4, s + 4, b(Blocks.OAK_FENCE));
        b.fill(-18, 5, s + 4, -14, 5, s + 4, b(Blocks.OAK_PLANKS));
        b.set(-16, 4, s + 4, b(Blocks.IRON_CHAIN)); b.set(-16, 3, s + 4, b(Blocks.IRON_CHAIN)); b.set(-16, 2, s + 4, b(Blocks.OAK_SLAB));
        // trees around
        for (int k = 0; k < 10; k++) {
            int x = b.rnd(-HALF + 2, HALF - 2), z = b.rnd(-HALF + 2, HALF - 2);
            if (Math.abs(x) < s + 3 && Math.abs(z) < s + 3) continue;
            b.fill(x, 1, z, x, 4, z, b(Blocks.OAK_LOG));
            b.ball(x, 5, z, 2.5, b(Blocks.OAK_LEAVES), false, false);
        }
    }

    static void classrooms(B b, int x1, int z1, int x2, int z2) {
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        BlockState wall = b(Blocks.CONCRETE.get(DyeColor.WHITE));
        b.fill(mx, 1, z1, mx, 5, z2, wall);
        b.fill(x1, 1, mz, x2, 5, mz, wall);
        int[][] rooms = { { x1 + 1, z1 + 1, mx - 1, mz - 1 }, { mx + 1, z1 + 1, x2 - 1, mz - 1 }, { x1 + 1, mz + 1, mx - 1, z2 - 1 }, { mx + 1, mz + 1, x2 - 1, z2 - 1 } };
        for (int[] r : rooms) {
            b.fill(r[0], 0, r[1], r[2], 0, r[3], b(Blocks.OAK_PLANKS));
            // chalkboard on one side
            int cx = (r[0] + r[2]) / 2;
            b.fill(cx - 4, 2, r[1] - 1, cx + 4, 4, r[1] - 1, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
            b.fill(cx - 3, 3, r[1] - 1, cx - 1, 3, r[1] - 1, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
            // teacher's desk
            b.fill(cx - 2, 1, r[1] + 2, cx + 2, 1, r[1] + 2, b(Blocks.DARK_OAK_PLANKS));
            b.set(cx, 2, r[1] + 2, b(Blocks.WOOL.get(DyeColor.RED))); // an apple for the teacher
            // rows of desks and chairs
            for (int x = r[0] + 2; x <= r[2] - 2; x += 3)
                for (int z = r[1] + 5; z <= r[3] - 2; z += 3) {
                    b.set(x, 1, z, b(Blocks.SPRUCE_FENCE));
                    b.set(x, 2, z, b(Blocks.SPRUCE_SLAB));
                    b.set(x, 1, z + 1, b(Blocks.OAK_STAIRS));
                }
            // doors into the corridor and between rooms
        }
        // door gaps
        b.fill(mx, 1, (z1 + mz) / 2 - 1, mx, 3, (z1 + mz) / 2 + 1, b(Blocks.AIR));
        b.fill(mx, 1, (mz + z2) / 2 - 1, mx, 3, (mz + z2) / 2 + 1, b(Blocks.AIR));
        b.fill((x1 + mx) / 2 - 1, 1, mz, (x1 + mx) / 2 + 1, 3, mz, b(Blocks.AIR));
        b.fill((mx + x2) / 2 - 1, 1, mz, (mx + x2) / 2 + 1, 3, mz, b(Blocks.AIR));
        // doors to the corridor (corridor is on the x2 side and the z side near 0)
        int zc = Math.abs(z1) < Math.abs(z2) ? z1 : z2;
        b.fill((x1 + mx) / 2 - 1, 1, zc, (x1 + mx) / 2 + 1, 3, zc, b(Blocks.AIR));
        b.fill((mx + x2) / 2 - 1, 1, zc, (mx + x2) / 2 + 1, 3, zc, b(Blocks.AIR));
        b.fill(x2, 1, (z1 + mz) / 2 - 1, x2, 3, (z1 + mz) / 2 + 1, b(Blocks.AIR));
        b.fill(x2, 1, (mz + z2) / 2 - 1, x2, 3, (mz + z2) / 2 + 1, b(Blocks.AIR));
    }

    static void gym(B b, int x1, int z1, int x2, int z2) {
        b.fill(x1 + 1, 0, z1 + 1, x2 - 1, 0, z2 - 1, b(Blocks.BIRCH_PLANKS));
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        // court lines
        b.fill(x1 + 3, 0, mz, x2 - 3, 0, mz, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        b.cyl(mx, mz, 5, 0, 0, b(Blocks.CONCRETE.get(DyeColor.WHITE)), true);
        b.walls(x1 + 3, 0, z1 + 3, x2 - 3, 0, z2 - 3, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        // basketball hoops
        for (int z : new int[] { z1 + 2, z2 - 2 }) {
            int dir = z < mz ? 1 : -1;
            b.fill(mx, 1, z, mx, 6, z, b(Blocks.IRON_BARS));
            b.fill(mx - 2, 7, z, mx + 2, 9, z, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
            b.fill(mx - 1, 7, z + dir, mx + 1, 7, z + dir * 3, b(Blocks.CONCRETE.get(DyeColor.RED)));
            b.set(mx, 7, z + dir * 2, b(Blocks.AIR));
        }
        // bleachers
        for (int i = 0; i < 4; i++) b.fill(x2 - 1 - i, 1, z1 + 6, x2 - 1 - i, 1 + i, z2 - 6, b(Blocks.SPRUCE_PLANKS));
        // gym mats and a climbing wall
        b.fill(x1 + 4, 1, z1 + 5, x1 + 8, 1, z1 + 9, b(Blocks.WOOL.get(DyeColor.BLUE)));
        b.fill(x1 + 1, 1, mz - 4, x1 + 1, 9, mz + 4, b(Blocks.STRIPPED_OAK_LOG));
        for (int y = 1; y < 9; y += 2) for (int z = mz - 4; z <= mz + 4; z += 3) b.set(x1 + 2, y, z + (y % 4 == 1 ? 0 : 1), b(Blocks.CONCRETE.get(DyeColor.ORANGE)));
        // slime jump pad
        b.fill(mx - 1, 0, mz + 8, mx + 1, 0, mz + 10, b(Blocks.SLIME_BLOCK));
    }

    static void cafeteria(B b, int x1, int z1, int x2, int z2) {
        b.checker(x1 + 1, z1 + 1, x2 - 1, z2 - 1, 0, 2, b(Blocks.DYED_TERRACOTTA.get(DyeColor.RED)), b(Blocks.DYED_TERRACOTTA.get(DyeColor.WHITE)));
        // long tables with benches
        for (int x = x1 + 5; x <= x2 - 6; x += 7) {
            for (int z = z1 + 6; z <= z2 - 6; z++) {
                b.set(x, 1, z, z % 4 == 0 ? b(Blocks.SPRUCE_FENCE) : b(Blocks.AIR));
                b.set(x, 2, z, b(Blocks.SPRUCE_SLAB));
                b.set(x - 2, 1, z, b(Blocks.OAK_SLAB));
                b.set(x + 2, 1, z, b(Blocks.OAK_SLAB));
            }
            // food on the tables
            for (int z = z1 + 7; z <= z2 - 7; z += 5) b.set(x, 3, z, b(Blocks.CAKE));
        }
        // the food counter
        b.fill(x1 + 3, 1, z2 - 3, x2 - 3, 2, z2 - 3, b(Blocks.IRON_BLOCK));
        for (int x = x1 + 4; x <= x2 - 4; x += 3) b.set(x, 3, z2 - 3, b(x % 2 == 0 ? Blocks.MELON : Blocks.PUMPKIN));
        b.textC("LUNCH", (x1 + x2) / 2, 4, z2 - 1, 1, 0, 1, b(Blocks.CONCRETE.get(DyeColor.YELLOW)));
    }

    // ======================= AREA 51 =======================

    static void area51(B b) {
        b.floor(b(Blocks.SAND));
        for (int k = 0; k < 900; k++) b.set(b.rnd(-HALF, HALF), 0, b.rnd(-HALF, HALF), b(b.r.nextBoolean() ? Blocks.RED_SAND : Blocks.SMOOTH_SANDSTONE));
        // fence all around
        int f = HALF - 2;
        b.walls(-f, 1, -f, f, 3, f, b(Blocks.IRON_BARS));
        b.walls(-f, 4, -f, f, 4, f, b(Blocks.IRON_CHAIN));
        b.text("KEEP OUT", -f + 3, 1, -f + 1, 1, 0, 1, b(Blocks.CONCRETE.get(DyeColor.RED)));
        // runway
        b.fill(-50, 0, 30, 50, 0, 42, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
        for (int x = -48; x <= 48; x += 6) b.fill(x, 0, 36, x + 3, 0, 36, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        for (int x = -50; x <= 50; x += 5) { b.set(x, 0, 30, b(Blocks.REDSTONE_LAMP)); b.set(x, 0, 42, b(Blocks.SEA_LANTERN)); }
        // two hangars
        hangar(b, -30, -32);
        hangar(b, 8, -32);
        b.textC("AREA 51", -20, 12, -22, 1, 0, 2, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
        // the crashed UFO with a crater
        int ux = 20, uz = 8;
        for (int x = -14; x <= 14; x++)
            for (int z = -14; z <= 14; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 13) {
                    int dep = (int) Math.max(0, 3 - d / 4);
                    b.fill(ux + x, 1 - dep, uz + z, ux + x, 0, uz + z, b(Blocks.AIR));
                    b.set(ux + x, -dep, uz + z, b(d < 8 ? Blocks.MAGMA_BLOCK : Blocks.COARSE_DIRT));
                }
            }
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(x * x + z * z);
                int tilt = x / 4;
                if (d <= 9) b.set(ux + x, 1 + tilt, uz + z, b(d > 8 ? Blocks.SEA_LANTERN : Blocks.IRON_BLOCK));
                if (d <= 6) b.set(ux + x, 2 + tilt, uz + z, b(Blocks.CONCRETE.get(DyeColor.LIGHT_GRAY)));
            }
        b.ball(ux, 3, uz, 4, b(Blocks.STAINED_GLASS.get(DyeColor.LIME)), true, true);
        b.fill(ux - 1, 3, uz - 1, ux + 1, 3, uz + 1, b(Blocks.AIR));
        b.set(ux, 3, uz, b(Blocks.CONCRETE.get(DyeColor.LIME))); b.set(ux, 4, uz, b(Blocks.SLIME_BLOCK)); // the pilot
        for (int k = 0; k < 6; k++) b.set(ux + b.rnd(-12, 12), 1, uz + b.rnd(-12, 12), b(Blocks.FIRE));
        // aliens standing around
        int[][] aliens = { { 2, 20 }, { 34, 0 }, { 38, 22 }, { -2, 6 } };
        for (int[] a : aliens) {
            b.set(a[0], 1, a[1], b(Blocks.CONCRETE.get(DyeColor.GREEN))); b.set(a[0], 2, a[1], b(Blocks.CONCRETE.get(DyeColor.GREEN)));
            b.set(a[0], 3, a[1], b(Blocks.CONCRETE.get(DyeColor.LIME))); b.set(a[0] - 1, 2, a[1], b(Blocks.STAINED_GLASS_PANE.get(DyeColor.GREEN))); b.set(a[0] + 1, 2, a[1], b(Blocks.STAINED_GLASS_PANE.get(DyeColor.GREEN)));
        }
        // watch towers in the corners
        for (int sx : new int[] { -1, 1 })
            for (int sz : new int[] { -1, 1 }) {
                int x = sx * (f - 5), z = sz * (f - 5);
                for (int[] leg : new int[][] { { 0, 0 }, { 3, 0 }, { 0, 3 }, { 3, 3 } }) b.fill(x + leg[0] * -sx, 1, z + leg[1] * -sz, x + leg[0] * -sx, 9, z + leg[1] * -sz, b(Blocks.IRON_BARS));
                b.fill(x, 10, z, x - 3 * sx, 10, z - 3 * sz, b(Blocks.SPRUCE_PLANKS));
                b.set(x - sx, 11, z - sz, b(Blocks.REDSTONE_LAMP));
                for (int y = 1; y <= 10; y++) b.set(x - sx, y, z - 4 * sz, b(Blocks.LADDER));
            }
        // radar dish
        b.fill(-40, 1, 10, -40, 8, 10, b(Blocks.IRON_BARS));
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) if (x * x + z * z <= 16) b.set(-40 + x, 9 + (x * x + z * z) / 6, 10 + z, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        // black vans and crates
        for (int[] v : new int[][] { { -10, 15 }, { -4, 15 }, { 40, -10 } }) {
            b.fill(v[0], 1, v[1], v[0] + 2, 2, v[1] + 4, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
            b.fill(v[0], 2, v[1], v[0] + 2, 2, v[1], b(Blocks.STAINED_GLASS.get(DyeColor.BLACK)));
        }
        for (int k = 0; k < 20; k++) { int x = b.rnd(-45, 45), z = b.rnd(-20, 25); b.fill(x, 1, z, x, b.rnd(1, 2), z, b(Blocks.BARREL)); }
    }

    static void hangar(B b, int x0, int z0) {
        int len = 22, rad = 9;
        for (int x = 0; x <= len; x++)
            for (int z = -rad; z <= rad; z++)
                for (int y = 0; y <= rad; y++) {
                    double d = Math.sqrt(z * z + y * y);
                    if (d <= rad && d > rad - 1.3) b.set(x0 + x, y + 1, z0 + z, b(x % 4 == 0 ? Blocks.CONCRETE.get(DyeColor.GRAY) : Blocks.CONCRETE.get(DyeColor.LIGHT_GRAY)));
                }
        b.fill(x0, 0, z0 - rad, x0 + len, 0, z0 + rad, b(Blocks.SMOOTH_STONE));
        // something secret inside
        b.fill(x0 + 8, 1, z0 - 2, x0 + 14, 1, z0 + 2, b(Blocks.IRON_BLOCK));
        b.ball(x0 + 11, 2, z0, 2.5, b(Blocks.GLASS), true, true);
        b.set(x0 + 11, 2, z0, b(Blocks.CONCRETE.get(DyeColor.LIME)));
    }

    // ======================= GIANT KITCHEN =======================

    static void kitchen(B b) {
        b.checker(-HALF, -HALF, HALF, HALF, 0, 4, b(Blocks.CONCRETE.get(DyeColor.WHITE)), b(Blocks.CONCRETE.get(DyeColor.BLACK)));
        // giant table
        int tx = 20, tz = -20, top = 16;
        for (int[] leg : new int[][] { { -14, -10 }, { 14, -10 }, { -14, 10 }, { 14, 10 } }) b.fill(tx + leg[0] - 1, 1, tz + leg[1] - 1, tx + leg[0] + 1, top - 1, tz + leg[1] + 1, b(Blocks.OAK_LOG));
        b.fill(tx - 17, top, tz - 13, tx + 17, top, tz + 13, b(Blocks.OAK_PLANKS));
        // a staircase of giant books up to the table
        Block[] books = { Blocks.WOOL.get(DyeColor.RED), Blocks.WOOL.get(DyeColor.BLUE), Blocks.WOOL.get(DyeColor.GREEN), Blocks.WOOL.get(DyeColor.YELLOW), Blocks.WOOL.get(DyeColor.PURPLE), Blocks.WOOL.get(DyeColor.ORANGE), Blocks.WOOL.get(DyeColor.CYAN), Blocks.WOOL.get(DyeColor.BROWN) };
        for (int i = 0; i < 8; i++) b.fill(tx - 26 + i * 1, 1, tz - 4 + 0, tx - 18 + 0, i * 2 + 2, tz + 4, b(books[i]));
        // plate, cheese and cup on the table
        b.cyl(tx - 4, tz, 7, top + 1, top + 1, b(Blocks.CONCRETE.get(DyeColor.WHITE)), false);
        b.cyl(tx - 4, tz, 7, top + 2, top + 2, b(Blocks.CONCRETE.get(DyeColor.WHITE)), true);
        for (int x = 0; x < 8; x++) for (int z = 0; z <= x; z++) b.fill(tx + 6 + x, top + 1, tz - 6 + z, tx + 6 + x, top + 5, tz - 6 + z, b(Blocks.CONCRETE.get(DyeColor.YELLOW)));
        b.fill(tx + 9, top + 3, tz - 5, tx + 9, top + 3, tz - 5, b(Blocks.AIR)); b.fill(tx + 12, top + 2, tz - 4, tx + 12, top + 2, tz - 4, b(Blocks.AIR));
        b.cyl(tx + 8, tz + 7, 3, top + 1, top + 7, b(Blocks.STAINED_GLASS.get(DyeColor.LIGHT_BLUE)), true);
        b.cyl(tx + 8, tz + 7, 2, top + 1, top + 3, b(Blocks.WATER), false);
        // giant fridge
        b.fill(-50, 1, -50, -36, 30, -40, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        b.fill(-49, 1, -40, -37, 26, -40, b(Blocks.AIR));
        b.fill(-49, 1, -49, -37, 26, -41, b(Blocks.AIR));
        b.fill(-49, 8, -49, -37, 8, -41, b(Blocks.GLASS)); b.fill(-49, 16, -49, -37, 16, -41, b(Blocks.GLASS));
        b.fill(-47, 9, -47, -44, 12, -44, b(Blocks.MELON)); b.fill(-42, 1, -47, -39, 5, -44, b(Blocks.PUMPKIN));
        b.fill(-47, 17, -47, -43, 19, -44, b(Blocks.BLUE_ICE));
        b.fill(-35, 14, -40, -35, 20, -40, b(Blocks.IRON_BLOCK)); // handle
        // giant toaster
        b.fill(-15, 1, 25, 5, 12, 35, b(Blocks.IRON_BLOCK));
        b.fill(-11, 6, 27, -1, 12, 28, b(Blocks.AIR)); b.fill(-11, 6, 31, -1, 12, 32, b(Blocks.AIR));
        b.fill(-10, 9, 27, -2, 14, 27, b(Blocks.STRIPPED_OAK_WOOD)); b.fill(-10, 9, 31, -2, 15, 31, b(Blocks.STRIPPED_OAK_WOOD));
        b.fill(6, 3, 29, 6, 4, 30, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
        // giant donut with sprinkles
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++)
                for (int y = -3; y <= 3; y++) {
                    double ring = Math.sqrt(x * x + z * z) - 5.5;
                    if (ring * ring + y * y <= 9) {
                        boolean icing = y >= 1;
                        Block c = icing ? (b.r.nextInt(8) == 0 ? (b.r.nextBoolean() ? Blocks.CONCRETE.get(DyeColor.LIGHT_BLUE) : Blocks.CONCRETE.get(DyeColor.YELLOW)) : Blocks.CONCRETE.get(DyeColor.PINK)) : Blocks.DYED_TERRACOTTA.get(DyeColor.ORANGE);
                        b.set(-25 + x, 4 + y, 20 + z, b(c));
                    }
                }
        // sugar cubes as cover
        for (int k = 0; k < 12; k++) { int x = b.rnd(-50, 50), z = b.rnd(-10, 50); if (Math.abs(x + 25) < 10 && Math.abs(z - 20) < 10) continue; b.fill(x, 1, z, x + 2, 3, z + 2, b(Blocks.CONCRETE_POWDER.get(DyeColor.WHITE))); }
        // a giant spoon on the floor
        b.fill(30, 1, 30, 48, 1, 31, b(Blocks.IRON_BLOCK));
        b.cyl(27, 30, 4, 1, 2, b(Blocks.IRON_BLOCK), false);
        b.textC("KITCHEN", 0, 22, -54, 1, 0, 2, b(Blocks.CONCRETE.get(DyeColor.RED)));
    }

    // ======================= BOUNCY CASTLE =======================

    static void bouncy(B b) {
        Block[] stripes = { Blocks.WOOL.get(DyeColor.RED), Blocks.WOOL.get(DyeColor.YELLOW), Blocks.WOOL.get(DyeColor.BLUE), Blocks.WOOL.get(DyeColor.LIME) };
        for (int x = -HALF; x <= HALF; x++) for (int z = -HALF; z <= HALF; z++) b.set(x, 0, z, b(stripes[Math.floorMod(x + z, 16) / 4]));
        // jump pads
        for (int k = 0; k < 26; k++) { int x = b.rnd(-48, 46), z = b.rnd(-48, 46); b.fill(x, 0, z, x + 2, 0, z + 2, b(Blocks.SLIME_BLOCK)); }
        // castle walls around with towers
        int w = 46;
        for (int i = -w; i <= w; i++)
            for (int y = 1; y <= 8; y++) {
                Block c = stripes[(Math.floorMod(i, 8) / 2)];
                if (!(Math.abs(i) < 4 && y < 6)) {
                    b.set(i, y, -w, b(c)); b.set(i, y, w, b(c)); b.set(-w, y, i, b(c)); b.set(w, y, i, b(c));
                }
                if (y == 8 && i % 2 == 0) { b.set(i, 9, -w, b(c)); b.set(i, 9, w, b(c)); b.set(-w, 9, i, b(c)); b.set(w, 9, i, b(c)); }
            }
        for (int sx : new int[] { -1, 1 })
            for (int sz : new int[] { -1, 1 }) {
                b.cyl(sx * w, sz * w, 5, 1, 14, b(Blocks.WOOL.get(DyeColor.RED)), true);
                b.cyl(sx * w, sz * w, 5, 15, 15, b(Blocks.WOOL.get(DyeColor.YELLOW)), false);
                for (int y = 16; y < 22; y++) b.cyl(sx * w, sz * w, 5 - (y - 16), y, y, b(Blocks.WOOL.get(DyeColor.BLUE)), false);
                b.fill(sx * w, 1, sz * w, sx * w, 14, sz * w, b(Blocks.SLIME_BLOCK)); // bouncy column inside
            }
        // the big bouncy middle with a slide
        b.cyl(0, 0, 10, 1, 1, b(Blocks.SLIME_BLOCK), false);
        b.cyl(0, 0, 10, 2, 6, b(Blocks.WOOL.get(DyeColor.PINK)), true);
        b.fill(-2, 2, -10, 2, 4, -10, b(Blocks.AIR));
        for (int i = 0; i < 12; i++) b.fill(12 + i, 12 - i, -2, 12 + i, 12 - i, 2, b(Blocks.WOOL.get(DyeColor.ORANGE)));
        b.fill(8, 1, -2, 11, 12, 2, b(Blocks.WOOL.get(DyeColor.CYAN)));
        for (int y = 1; y <= 12; y++) b.set(7, y, 0, b(Blocks.LADDER));
        // balloons
        for (int k = 0; k < 10; k++) {
            int x = b.rnd(-40, 40), z = b.rnd(-40, 40), h = b.rnd(10, 18);
            b.fill(x, 1, z, x, h - 1, z, b(Blocks.STAINED_GLASS_PANE.get(DyeColor.WHITE)));
            b.ball(x, h + 1, z, 1.6, b(stripes[k % 4]), false, false);
        }
        b.textC("BOUNCY CASTLE", 0, 12, -w, 1, 0, 1, b(Blocks.WOOL.get(DyeColor.WHITE)));
    }

    // ======================= SUPERMARKET =======================

    static void supermarket(B b) {
        b.checker(-HALF, -HALF, HALF, HALF, 0, 2, b(Blocks.CONCRETE.get(DyeColor.WHITE)), b(Blocks.CONCRETE.get(DyeColor.LIGHT_GRAY)));
        int w = 50;
        b.walls(-w, 1, -w, w, 9, w, b(Blocks.CONCRETE.get(DyeColor.LIGHT_GRAY)));
        b.fill(-w, 3, w, w, 6, w, b(Blocks.GLASS)); // shop windows
        b.fill(-4, 1, w, 4, 5, w, b(Blocks.AIR));  // entrance
        b.textC("SUPERMARKET", 0, 11, w, 1, 0, 1, b(Blocks.CONCRETE.get(DyeColor.RED)));
        b.textC("SALE", 0, 6, -w + 1, 1, 0, 2, b(Blocks.CONCRETE.get(DyeColor.YELLOW)));
        // aisles of shelves full of stuff
        Block[] stuff = { Blocks.MELON, Blocks.PUMPKIN, Blocks.HAY_BLOCK, Blocks.BOOKSHELF, Blocks.DYED_TERRACOTTA.get(DyeColor.RED), Blocks.CAKE, Blocks.HONEY_BLOCK,
            Blocks.BARREL, Blocks.DYED_TERRACOTTA.get(DyeColor.LIME), Blocks.DYED_TERRACOTTA.get(DyeColor.ORANGE), Blocks.BROWN_MUSHROOM_BLOCK, Blocks.DRIED_KELP_BLOCK, Blocks.DYED_TERRACOTTA.get(DyeColor.BLUE), Blocks.SPONGE };
        for (int x = -42; x <= 30; x += 8) {
            for (int z = -40; z <= 16; z++) {
                if (z == -12 || z == -11 || z == -10) continue; // a gap in the middle of every aisle
                b.set(x, 1, z, b(Blocks.SPRUCE_PLANKS)); b.set(x + 1, 1, z, b(Blocks.SPRUCE_PLANKS));
                for (int y = 2; y <= 4; y++) {
                    Block s = stuff[Math.floorMod(x * 7 + z / 3 + y * 5, stuff.length)];
                    b.set(x, y, z, b(s)); b.set(x + 1, y, z, b(s));
                }
                b.set(x, 5, z, b(Blocks.SPRUCE_SLAB)); b.set(x + 1, 5, z, b(Blocks.SPRUCE_SLAB));
            }
            b.set(x, 7, -12, b(Blocks.CONCRETE.get(DyeColor.YELLOW))); b.set(x + 1, 7, -12, b(Blocks.CONCRETE.get(DyeColor.YELLOW))); // aisle signs
        }
        // freezers at the back
        b.fill(-46, 1, -48, 46, 3, -46, b(Blocks.PACKED_ICE));
        b.fill(-46, 4, -46, 46, 4, -46, b(Blocks.STAINED_GLASS.get(DyeColor.LIGHT_BLUE)));
        // checkouts
        for (int x = -40; x <= 40; x += 10) {
            b.fill(x, 1, 30, x + 1, 1, 38, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
            b.set(x, 2, 36, b(Blocks.IRON_BLOCK));
            b.set(x + 1, 2, 36, b(Blocks.STAINED_GLASS.get(DyeColor.LIME)));
            b.fill(x, 1, 33, x, 3, 33, b(Blocks.IRON_BARS));
            b.set(x, 4, 33, b(Blocks.REDSTONE_LAMP));
        }
        // shopping trolleys and price tags
        for (int k = 0; k < 14; k++) {
            int x = b.rnd(-45, 45), z = b.rnd(18, 28);
            b.fill(x, 1, z, x + 1, 2, z + 2, b(Blocks.IRON_BARS));
            b.set(x, 1, z, b(Blocks.HOPPER));
        }
        // lights hanging from the sky
        for (int x = -40; x <= 40; x += 10) for (int z = -40; z <= 40; z += 10) { b.fill(x, 11, z, x, 13, z, b(Blocks.IRON_CHAIN)); b.set(x, 10, z, b(Blocks.SEA_LANTERN)); }
        // parking lot in front
        b.fill(-HALF, 0, w + 1, HALF, 0, HALF, b(Blocks.CONCRETE.get(DyeColor.GRAY)));
        for (int x = -HALF + 2; x <= HALF; x += 5) b.set(x, 0, w + 3, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
    }

    // ======================= MOON BASE =======================

    static void moon(B b) {
        b.floor(b(Blocks.END_STONE));
        for (int k = 0; k < 1200; k++) b.set(b.rnd(-HALF, HALF), 0, b.rnd(-HALF, HALF), b(b.r.nextBoolean() ? Blocks.CONCRETE_POWDER.get(DyeColor.LIGHT_GRAY) : Blocks.END_STONE_BRICKS));
        // craters
        for (int k = 0; k < 9; k++) {
            int cx = b.rnd(-45, 45), cz = b.rnd(-45, 45), rad = b.rnd(4, 8);
            if (Math.abs(cx) < 14 && Math.abs(cz) < 14) continue;
            for (int x = -rad - 1; x <= rad + 1; x++)
                for (int z = -rad - 1; z <= rad + 1; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= rad) {
                        int dep = (int) Math.round((1 - d / rad) * 3);
                        if (dep > 0) { b.fill(cx + x, -dep + 1, cz + z, cx + x, 0, cz + z, b(Blocks.AIR)); b.set(cx + x, -dep, cz + z, b(Blocks.SMOOTH_BASALT)); }
                    } else if (d <= rad + 1) b.set(cx + x, 1, cz + z, b(Blocks.END_STONE));
                }
        }
        // glass domes you can go into
        int[][] domes = { { -30, -25, 8 }, { 28, -28, 7 }, { -28, 28, 6 } };
        for (int[] d : domes) {
            b.cyl(d[0], d[1], d[2], 0, 0, b(Blocks.CONCRETE.get(DyeColor.WHITE)), false);
            b.ball(d[0], 1, d[1], d[2], b(Blocks.STAINED_GLASS.get(DyeColor.LIGHT_BLUE)), true, true);
            b.fill(d[0] - 1, 1, d[1] + d[2] - 1, d[0] + 1, 3, d[1] + d[2], b(Blocks.AIR));
            b.set(d[0], 1, d[1], b(Blocks.SEA_LANTERN));
            b.fill(d[0] - 2, 1, d[1] - 3, d[0] + 2, 1, d[1] - 3, b(Blocks.IRON_BLOCK)); // control desk
            b.set(d[0], 2, d[1] - 3, b(Blocks.REDSTONE_LAMP));
        }
        // tunnels between domes
        b.fill(-22, 1, -27, 20, 3, -26, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        b.fill(-22, 1, -27, 20, 2, -26, b(Blocks.AIR));
        // the rocket
        int rx = 30, rz = 26;
        b.fill(rx - 6, 0, rz - 6, rx + 6, 1, rz + 6, b(Blocks.POLISHED_BLACKSTONE));
        b.cyl(rx, rz, 3, 2, 26, b(Blocks.CONCRETE.get(DyeColor.WHITE)), true);
        for (int y = 27; y < 32; y++) b.cyl(rx, rz, 3 - (y - 27) * 0.6, y, y, b(Blocks.CONCRETE.get(DyeColor.RED)), false);
        for (int y = 6; y < 26; y += 6) b.set(rx, y, rz - 3, b(Blocks.STAINED_GLASS.get(DyeColor.LIGHT_BLUE)));
        for (int[] fin : new int[][] { { 4, 0 }, { -4, 0 }, { 0, 4 }, { 0, -4 } }) b.fill(rx + fin[0], 2, rz + fin[1], rx + fin[0] + Integer.signum(fin[0]), 7, rz + fin[1] + Integer.signum(fin[1]), b(Blocks.CONCRETE.get(DyeColor.RED)));
        b.fill(rx, 2, rz + 3, rx, 3, rz + 3, b(Blocks.AIR));
        for (int y = 2; y <= 25; y++) b.set(rx, y, rz + 2, b(Blocks.LADDER));
        // flag
        b.fill(0, 1, 0, 0, 8, 0, b(Blocks.IRON_BARS));
        b.fill(1, 5, 0, 5, 8, 0, b(Blocks.WOOL.get(DyeColor.BLUE)));
        b.fill(1, 8, 0, 5, 8, 0, b(Blocks.WOOL.get(DyeColor.WHITE))); b.fill(1, 6, 0, 5, 6, 0, b(Blocks.WOOL.get(DyeColor.WHITE)));
        // earth in the sky
        b.ball(-40, 45, -40, 7, b(Blocks.CONCRETE.get(DyeColor.BLUE)), false, false);
        for (int k = 0; k < 60; k++) b.set(-40 + b.rnd(-6, 6), 45 + b.rnd(-6, 6), -40 + b.rnd(-6, 6), b(Blocks.CONCRETE.get(DyeColor.GREEN)));
        b.textC("MOON BASE", 0, 6, 54, 1, 0, 2, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
    }

    // ======================= PIRATE SHIP =======================

    static void pirate(B b) {
        b.fill(-HALF, -2, -HALF, HALF, -2, HALF, b(Blocks.SAND));
        b.fill(-HALF, -1, -HALF, HALF, 0, HALF, b(Blocks.WATER));
        // the big ship
        int len = 26, wid = 7, deck = 6;
        for (int x = -len; x <= len; x++) {
            double t = Math.abs(x) / (double) len;
            int halfW = (int) Math.round(wid * Math.sqrt(Math.max(0, 1 - Math.pow(Math.max(0, t - 0.4) / 0.6, 2))));
            if (halfW < 1) halfW = 1;
            for (int z = -halfW; z <= halfW; z++) {
                int bottom = Math.abs(z) > halfW - 2 ? 0 : -1;
                b.fill(x, bottom, z, x, deck - 1, z, b(Math.abs(z) == halfW ? Blocks.DARK_OAK_PLANKS : Blocks.AIR));
                b.set(x, bottom, z, b(Blocks.DARK_OAK_PLANKS));
                b.set(x, deck, z, b(Blocks.SPRUCE_PLANKS));
                if (Math.abs(z) == halfW) b.set(x, deck + 1, z, b(Blocks.DARK_OAK_FENCE));
                if (Math.abs(z) == halfW && x % 6 == 0 && Math.abs(x) < len - 6) {
                    b.set(x, 3, z, b(Blocks.AIR));
                    b.set(x, 3, z - Integer.signum(z), b(Blocks.POLISHED_BLACKSTONE)); // cannons
                }
            }
        }
        // captain's cabin at the back
        b.fill(-len + 3, deck + 1, -4, -len + 10, deck + 5, 4, b(Blocks.DARK_OAK_PLANKS));
        b.fill(-len + 4, deck + 1, -3, -len + 9, deck + 4, 3, b(Blocks.AIR));
        b.fill(-len + 10, deck + 1, -1, -len + 10, deck + 3, 1, b(Blocks.AIR));
        b.fill(-len + 3, deck + 6, -4, -len + 10, deck + 6, 4, b(Blocks.SPRUCE_PLANKS));
        b.set(-len + 6, deck + 1, 0, b(Blocks.CHEST));
        b.set(-len + 3, deck + 3, 0, b(Blocks.GLASS_PANE));
        // masts and sails
        for (int mx : new int[] { -6, 8 }) {
            b.fill(mx, deck + 1, 0, mx, deck + 22, 0, b(Blocks.SPRUCE_LOG));
            for (int y = deck + 6; y <= deck + 18; y++) {
                int sw = 6 - Math.abs(y - (deck + 12)) / 3;
                b.fill(mx + 1, y, -sw, mx + 1, y, sw, b(Blocks.WOOL.get(DyeColor.WHITE)));
            }
            b.fill(mx - 1, deck + 19, -1, mx + 1, deck + 19, 1, b(Blocks.SPRUCE_PLANKS));
            b.walls(mx - 1, deck + 20, -1, mx + 1, deck + 20, 1, b(Blocks.SPRUCE_FENCE));
            for (int y = deck + 1; y <= deck + 18; y++) b.set(mx - 1, y, 0, b(Blocks.LADDER));
        }
        b.fill(-6, deck + 15, 0, -6, deck + 17, 0, b(Blocks.WOOL.get(DyeColor.BLACK))); // pirate flag
        b.fill(-5, deck + 21, 0, -1, deck + 22, 0, b(Blocks.WOOL.get(DyeColor.BLACK)));
        b.set(-3, deck + 21, 0, b(Blocks.WOOL.get(DyeColor.WHITE)));
        // the plank
        b.fill(0, deck, 8, 0, deck, 12, b(Blocks.OAK_SLAB));
        b.set(0, deck + 1, 7, b(Blocks.AIR));
        // ramps from the water
        for (int i = 0; i < 6; i++) { b.set(len - 8 + i, i + 1, wid + 1, b(Blocks.OAK_PLANKS)); b.set(-len + 8 - i, i + 1, -wid - 1, b(Blocks.OAK_PLANKS)); }
        // islands with palm trees and treasure
        int[][] islands = { { -38, 34, 9 }, { 38, -34, 8 }, { 40, 38, 5 } };
        for (int[] is : islands) {
            for (int x = -is[2]; x <= is[2]; x++) for (int z = -is[2]; z <= is[2]; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= is[2]) b.fill(is[0] + x, -1, is[1] + z, is[0] + x, d < is[2] - 2 ? 1 : 0, is[1] + z, b(Blocks.SAND));
            }
            b.fill(is[0], 2, is[1], is[0], 8, is[1], b(Blocks.JUNGLE_LOG));
            b.set(is[0] + 1, 9, is[1], b(Blocks.JUNGLE_LOG));
            for (int[] lf : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 }, { 3, -1 }, { -3, -1 }, { -1, 3 }, { -1, -3 } })
                b.set(is[0] + lf[0], 9, is[1] + lf[1], b(Blocks.JUNGLE_LEAVES));
            b.set(is[0] + 2, 2, is[1] + 2, b(Blocks.CHEST));
            b.set(is[0] - 2, 2, is[1] - 1, b(Blocks.GOLD_BLOCK));
        }
        // floating barrels as cover
        for (int k = 0; k < 16; k++) { int x = b.rnd(-50, 50), z = b.rnd(-50, 50); if (Math.abs(x) < len + 3 && Math.abs(z) < wid + 3) continue; b.set(x, 1, z, b(Blocks.BARREL)); b.set(x, 0, z, b(Blocks.BARREL)); }
        b.textC("PIRATES", 0, 4, -54, 1, 0, 2, b(Blocks.CONCRETE.get(DyeColor.BLACK)));
    }

    // ======================= GIANT BATHROOM =======================

    static void bathroom(B b) {
        b.checker(-HALF, -HALF, HALF, HALF, 0, 3, b(Blocks.CONCRETE.get(DyeColor.WHITE)), b(Blocks.CONCRETE.get(DyeColor.LIGHT_BLUE)));
        // giant toilet
        int tx = -28, tz = -28;
        b.cyl(tx, tz, 4, 1, 6, b(Blocks.QUARTZ_BLOCK), false);
        for (int x = -9; x <= 9; x++) for (int z = -7; z <= 7; z++) {
            double d = Math.sqrt(x * x / 81.0 + z * z / 49.0);
            if (d <= 1) b.set(tx + x, 7, tz + z, b(d > 0.7 ? Blocks.QUARTZ_BLOCK : Blocks.WATER));
            if (d <= 1 && d > 0.75) b.set(tx + x, 8, tz + z, b(Blocks.CONCRETE.get(DyeColor.WHITE))); // the seat
        }
        b.fill(tx - 14, 1, tz - 7, tx - 10, 18, tz + 7, b(Blocks.QUARTZ_BLOCK)); // tank
        b.fill(tx - 9, 16, tz - 5, tx - 9, 16, tz - 3, b(Blocks.IRON_BLOCK));    // flush handle
        for (int i = 0; i < 7; i++) b.fill(tx + 10 + i, 1, tz - 2, tx + 10 + i, 7 - i, tz + 2, b(Blocks.QUARTZ_STAIRS)); // steps up
        // giant bathtub with a rubber duck
        int bx = 20, bz = -30;
        b.fill(bx - 18, 1, bz - 10, bx + 18, 9, bz + 10, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        b.fill(bx - 16, 2, bz - 8, bx + 16, 9, bz + 8, b(Blocks.AIR));
        b.fill(bx - 16, 2, bz - 8, bx + 16, 7, bz + 8, b(Blocks.WATER));
        for (int x = -16; x <= 16; x++) for (int z = -8; z <= 8; z++) if (b.r.nextInt(6) == 0) b.set(bx + x, 8, bz + z, b(Blocks.STAINED_GLASS.get(DyeColor.WHITE))); // bubbles
        b.ball(bx, 9, bz, 3.5, b(Blocks.WOOL.get(DyeColor.YELLOW)), false, false);
        b.ball(bx + 3, 13, bz, 2.4, b(Blocks.WOOL.get(DyeColor.YELLOW)), false, false);
        b.fill(bx + 5, 13, bz - 1, bx + 6, 13, bz + 1, b(Blocks.WOOL.get(DyeColor.ORANGE)));
        b.set(bx + 5, 14, bz - 1, b(Blocks.WOOL.get(DyeColor.BLACK))); b.set(bx + 5, 14, bz + 1, b(Blocks.WOOL.get(DyeColor.BLACK)));
        b.fill(bx + 17, 10, bz - 1, bx + 17, 16, bz + 1, b(Blocks.IRON_BLOCK)); b.fill(bx + 14, 16, bz - 1, bx + 17, 16, bz + 1, b(Blocks.IRON_BLOCK)); // tap
        for (int i = 0; i < 8; i++) b.fill(bx - 26 + i, 1, bz - 2, bx - 26 + i, i + 1, bz + 2, b(Blocks.CONCRETE.get(DyeColor.PINK))); // soap steps
        // giant soap bar
        b.fill(-10, 1, 10, 4, 4, 18, b(Blocks.CONCRETE.get(DyeColor.PINK)));
        b.fill(-9, 5, 11, 3, 5, 17, b(Blocks.CONCRETE_POWDER.get(DyeColor.PINK)));
        // giant toilet paper roll
        b.cyl(30, 20, 7, 1, 14, b(Blocks.WOOL.get(DyeColor.WHITE)), false);
        b.cyl(30, 20, 2.5, 1, 14, b(Blocks.AIR), false);
        b.cyl(30, 20, 2.5, 1, 14, b(Blocks.DYED_TERRACOTTA.get(DyeColor.BROWN)), true);
        b.fill(37, 1, 14, 37, 1, 26, b(Blocks.WOOL.get(DyeColor.WHITE))); b.fill(38, 0, 14, 46, 0, 26, b(Blocks.WOOL.get(DyeColor.WHITE)));
        // giant toothbrush
        b.fill(-45, 1, 20, -45, 1, 50, b(Blocks.CONCRETE.get(DyeColor.CYAN)));
        b.fill(-46, 2, 44, -44, 4, 50, b(Blocks.WOOL.get(DyeColor.WHITE)));
        // a sink against the wall
        b.fill(-20, 1, 48, 0, 10, 54, b(Blocks.CONCRETE.get(DyeColor.WHITE)));
        b.fill(-17, 7, 49, -3, 10, 53, b(Blocks.AIR));
        b.fill(-17, 7, 49, -3, 8, 53, b(Blocks.WATER));
        b.fill(-11, 11, 53, -9, 14, 54, b(Blocks.IRON_BLOCK));
        // the mirror
        b.fill(-24, 12, 55, 4, 26, 55, b(Blocks.STAINED_GLASS.get(DyeColor.LIGHT_GRAY)));
        b.textC("BATHROOM", -10, 28, 55, 1, 0, 1, b(Blocks.CONCRETE.get(DyeColor.BLUE)));
    }

    // ======================= tiny pixel font =======================

    static final class Font {
        static String[] glyph(char c) {
            return switch (c) {
                case 'A' -> new String[] { ".#.", "#.#", "###", "#.#", "#.#" };
                case 'B' -> new String[] { "##.", "#.#", "##.", "#.#", "##." };
                case 'C' -> new String[] { ".##", "#..", "#..", "#..", ".##" };
                case 'D' -> new String[] { "##.", "#.#", "#.#", "#.#", "##." };
                case 'E' -> new String[] { "###", "#..", "##.", "#..", "###" };
                case 'F' -> new String[] { "###", "#..", "##.", "#..", "#.." };
                case 'G' -> new String[] { ".##", "#..", "#.#", "#.#", ".##" };
                case 'H' -> new String[] { "#.#", "#.#", "###", "#.#", "#.#" };
                case 'I' -> new String[] { "###", ".#.", ".#.", ".#.", "###" };
                case 'J' -> new String[] { "..#", "..#", "..#", "#.#", ".#." };
                case 'K' -> new String[] { "#.#", "#.#", "##.", "#.#", "#.#" };
                case 'L' -> new String[] { "#..", "#..", "#..", "#..", "###" };
                case 'M' -> new String[] { "#.#", "###", "###", "#.#", "#.#" };
                case 'N' -> new String[] { "##.", "#.#", "#.#", "#.#", "#.#" };
                case 'O' -> new String[] { ".#.", "#.#", "#.#", "#.#", ".#." };
                case 'P' -> new String[] { "##.", "#.#", "##.", "#..", "#.." };
                case 'Q' -> new String[] { ".#.", "#.#", "#.#", "##.", ".##" };
                case 'R' -> new String[] { "##.", "#.#", "##.", "#.#", "#.#" };
                case 'S' -> new String[] { ".##", "#..", ".#.", "..#", "##." };
                case 'T' -> new String[] { "###", ".#.", ".#.", ".#.", ".#." };
                case 'U' -> new String[] { "#.#", "#.#", "#.#", "#.#", "###" };
                case 'V' -> new String[] { "#.#", "#.#", "#.#", "#.#", ".#." };
                case 'W' -> new String[] { "#.#", "#.#", "###", "###", "#.#" };
                case 'X' -> new String[] { "#.#", "#.#", ".#.", "#.#", "#.#" };
                case 'Y' -> new String[] { "#.#", "#.#", ".#.", ".#.", ".#." };
                case 'Z' -> new String[] { "###", "..#", ".#.", "#..", "###" };
                case '0' -> new String[] { "###", "#.#", "#.#", "#.#", "###" };
                case '1' -> new String[] { ".#.", "##.", ".#.", ".#.", "###" };
                case '2' -> new String[] { "##.", "..#", ".#.", "#..", "###" };
                case '3' -> new String[] { "##.", "..#", ".#.", "..#", "##." };
                case '4' -> new String[] { "#.#", "#.#", "###", "..#", "..#" };
                case '5' -> new String[] { "###", "#..", "##.", "..#", "##." };
                case '6' -> new String[] { ".##", "#..", "###", "#.#", "###" };
                case '7' -> new String[] { "###", "..#", ".#.", ".#.", ".#." };
                case '8' -> new String[] { "###", "#.#", "###", "#.#", "###" };
                case '9' -> new String[] { "###", "#.#", "###", "..#", "##." };
                case '!' -> new String[] { ".#.", ".#.", ".#.", "...", ".#." };
                default -> new String[] { "...", "...", "...", "...", "..." };
            };
        }
    }
}
