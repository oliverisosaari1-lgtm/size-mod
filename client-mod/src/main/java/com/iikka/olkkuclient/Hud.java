package com.iikka.olkkuclient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The black and white HUD: info lines, keystrokes and armor. */
public final class Hud {
    static final int BG = 0x90000000, WHITE = 0xFFFFFFFF, GREY = 0xFFAAAAAA;

    static void box(GuiGraphicsExtractor g, Font f, int x, int y, int w, String text) {
        g.fill(x, y, x + w, y + 12, BG);
        g.text(f, text, x + 3, y + 2, WHITE, false);
    }

    public static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Config c = Config.INSTANCE;
        Font f = mc.font;
        int y = 4;
        if (c.fps) { box(g, f, 4, y, 70, mc.getFps() + " FPS"); y += 14; }
        if (c.cps) { box(g, f, 4, y, 70, OlkkuClient.cps(true) + " | " + OlkkuClient.cps(false) + " CPS"); y += 14; }
        if (c.ping && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (info != null) { box(g, f, 4, y, 70, info.getLatency() + " ms"); y += 14; }
        }
        if (c.coords) {
            String s = String.format("%d %d %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            box(g, f, 4, y, Math.max(70, f.width(s) + 6), s); y += 14;
        }
        if (c.direction) {
            box(g, f, 4, y, 70, "Facing " + mc.player.getDirection().getName()); y += 14;
        }
        if (c.keystrokes) keystrokes(g, mc, f, 4, y + 2);
        if (c.armor) armor(g, mc, f);
    }

    static void key(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, String label, boolean down) {
        g.fill(x, y, x + w, y + h, down ? 0xEEFFFFFF : BG);
        g.outline(x, y, w, h, 0x60FFFFFF);
        int color = down ? 0xFF000000 : WHITE;
        g.text(f, label, x + (w - f.width(label)) / 2, y + (h - 8) / 2, color, false);
    }

    static void keystrokes(GuiGraphicsExtractor g, Minecraft mc, Font f, int x, int y) {
        var o = mc.options;
        int s = 22, gap = 2;
        key(g, f, x + s + gap, y, s, s, "W", o.keyUp.isDown());
        y += s + gap;
        key(g, f, x, y, s, s, "A", o.keyLeft.isDown());
        key(g, f, x + s + gap, y, s, s, "S", o.keyDown.isDown());
        key(g, f, x + 2 * (s + gap), y, s, s, "D", o.keyRight.isDown());
        y += s + gap;
        int half = (3 * s + 2 * gap - gap) / 2;
        key(g, f, x, y, half, s, "LMB " + OlkkuClient.cps(true), o.keyAttack.isDown());
        key(g, f, x + half + gap, y, half, s, "RMB " + OlkkuClient.cps(false), o.keyUse.isDown());
        y += s + gap;
        key(g, f, x, y, 3 * s + 2 * gap, 12, "-----", o.keyJump.isDown());
    }

    static void armor(GuiGraphicsExtractor g, Minecraft mc, Font f) {
        EquipmentSlot[] slots = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND };
        int x = g.guiWidth() - 22, y = g.guiHeight() / 2 - 45;
        for (EquipmentSlot s : slots) {
            ItemStack st = mc.player.getItemBySlot(s);
            if (st.isEmpty()) continue;
            g.fill(x - 2, y - 1, x + 18, y + 17, BG);
            g.item(st, x, y);
            if (st.isDamageableItem()) {
                int left = st.getMaxDamage() - st.getDamageValue();
                String t = String.valueOf(left);
                int pct = left * 100 / Math.max(1, st.getMaxDamage());
                int col = pct > 50 ? WHITE : pct > 20 ? 0xFFFFFF55 : 0xFFFF5555;
                g.fill(x - 4 - f.width(t), y + 3, x - 2, y + 14, BG);
                g.text(f, t, x - 3 - f.width(t), y + 5, col, false);
            }
            y += 19;
        }
    }
}
