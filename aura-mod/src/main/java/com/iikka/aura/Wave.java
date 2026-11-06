package com.iikka.aura;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/** /wave: a big sheet of diamond blocks under you that ripples like water and moves with you. */
public class Wave {
    static final String TAG = "aura_wave";
    private static final int R = 7; // 15x15 sheet
    private static class Sheet { final List<Display.BlockDisplay> blocks = new ArrayList<>(); ServerLevel level; }
    private static final Map<UUID, Sheet> sheets = new HashMap<>();

    static boolean toggle(ServerPlayer p) {
        Sheet s = sheets.remove(p.getUUID());
        if (s != null) { s.blocks.forEach(b -> b.discard()); return false; }
        sheets.put(p.getUUID(), new Sheet());
        return true;
    }

    static void remove(UUID id) {
        Sheet s = sheets.remove(id);
        if (s != null) s.blocks.forEach(b -> b.discard());
    }

    static void clear() { for (UUID id : new ArrayList<>(sheets.keySet())) remove(id); }

    static boolean ours(net.minecraft.world.entity.Entity e) {
        for (Sheet s : sheets.values()) if (s.blocks.contains(e)) return true;
        return false;
    }

    static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Sheet s = sheets.get(p.getUUID());
            if (s == null) continue;
            if (s.level != p.level() || s.blocks.isEmpty() || s.blocks.get(0).isRemoved()) {
                s.blocks.forEach(b -> b.discard());
                s.blocks.clear();
                s.level = (ServerLevel) p.level();
                for (int i = 0; i < (2 * R + 1) * (2 * R + 1); i++) {
                    Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(s.level, EntitySpawnReason.COMMAND);
                    if (d == null) continue;
                    d.setBlockState(Blocks.DIAMOND_BLOCK.defaultBlockState());
                    d.setPosRotInterpolationDuration(3);
                    d.addTag(TAG);
                    d.setPos(p.getX(), p.getY() - 1, p.getZ());
                    s.blocks.add(d);
                    s.level.addFreshEntity(d);
                }
            }
            // You float gently on the wave and never take fall damage
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false));
            p.fallDistance = 0;
            int bx = (int) Math.floor(p.getX()), bz = (int) Math.floor(p.getZ());
            double base = Math.floor(p.getY()) - 1;
            int i = 0;
            for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
                if (i >= s.blocks.size()) break;
                double wx = bx + x, wz = bz + z;
                // Ripple: waves roll across the sheet, edges move more than the middle
                double edge = Math.sqrt(x * x + z * z) / R;
                double y = base + Math.sin(wx * 0.45 + wz * 0.3 + t * 0.25) * 1.6 * edge + Math.sin(wz * 0.5 - t * 0.2) * 0.6 * edge;
                s.blocks.get(i++).teleportTo(wx, Math.round(y * 2) / 2.0, wz);
            }
        }
    }
}
