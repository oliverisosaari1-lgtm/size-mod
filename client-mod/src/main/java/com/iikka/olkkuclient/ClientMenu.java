package com.iikka.olkkuclient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Right Shift: the Olkku's Client menu. Black and white, two tabs: Mods and Capes. */
public class ClientMenu extends Screen {
    private static int tab = 0;

    private record Mod(String name, String info, Supplier<Boolean> get, Consumer<Boolean> set) { }

    private static final List<Mod> MODS = new ArrayList<>();
    static {
        Config c = Config.INSTANCE;
        MODS.add(new Mod("FPS", "Frames per second", () -> Config.INSTANCE.fps, v -> Config.INSTANCE.fps = v));
        MODS.add(new Mod("CPS", "Clicks per second", () -> Config.INSTANCE.cps, v -> Config.INSTANCE.cps = v));
        MODS.add(new Mod("Keystrokes", "Shows WASD and clicks", () -> Config.INSTANCE.keystrokes, v -> Config.INSTANCE.keystrokes = v));
        MODS.add(new Mod("Armor", "Armor and durability", () -> Config.INSTANCE.armor, v -> Config.INSTANCE.armor = v));
        MODS.add(new Mod("Ping", "Your ping in ms", () -> Config.INSTANCE.ping, v -> Config.INSTANCE.ping = v));
        MODS.add(new Mod("Coordinates", "X Y Z", () -> Config.INSTANCE.coords, v -> Config.INSTANCE.coords = v));
        MODS.add(new Mod("Direction", "Which way you face", () -> Config.INSTANCE.direction, v -> Config.INSTANCE.direction = v));
        MODS.add(new Mod("Auto Sprint", "Always sprint forward", () -> Config.INSTANCE.autoSprint, v -> Config.INSTANCE.autoSprint = v));
        MODS.add(new Mod("Zoom", "Hold C to zoom", () -> Config.INSTANCE.zoom, v -> Config.INSTANCE.zoom = v));
    }

    public ClientMenu() { super(Component.literal("Olkku's Client")); }

    // layout
    private int px, py, pw, ph;

    @Override
    protected void init() {
        pw = Math.min(360, width - 20);
        ph = Math.min(240, height - 20);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x99000000);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private int cols() { return 3; }
    private int tileW() { return (pw - 16 - (cols() - 1) * 6) / cols(); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.fill(px, py, px + pw, py + ph, 0xF0101010);
        g.outline(px, py, pw, ph, 0xFFFFFFFF);
        g.text(font, "OLKKU'S CLIENT", px + 8, py + 8, 0xFFFFFFFF, false);
        String[] tabs = { "MODS", "CAPES" };
        int tx = px + pw - 8;
        for (int i = tabs.length - 1; i >= 0; i--) {
            int w = font.width(tabs[i]) + 12;
            tx -= w;
            boolean on = tab == i, hover = in(mx, my, tx, py + 4, w, 16);
            g.fill(tx, py + 4, tx + w, py + 20, on ? 0xFFFFFFFF : hover ? 0x40FFFFFF : 0x00000000);
            g.outline(tx, py + 4, w, 16, 0xFFFFFFFF);
            g.text(font, tabs[i], tx + 6, py + 8, on ? 0xFF000000 : 0xFFFFFFFF, false);
            tx -= 4;
        }
        g.fill(px + 8, py + 24, px + pw - 8, py + 25, 0x80FFFFFF);
        if (tab == 0) drawMods(g, mx, my); else drawCapes(g, mx, my);
        g.text(font, "Right Shift or Esc to close", px + 8, py + ph - 12, 0xFF888888, false);
    }

    private void drawMods(GuiGraphicsExtractor g, int mx, int my) {
        int tw = tileW(), th = 34;
        for (int i = 0; i < MODS.size(); i++) {
            Mod m = MODS.get(i);
            int x = px + 8 + (i % cols()) * (tw + 6), y = py + 32 + (i / cols()) * (th + 6);
            boolean on = m.get().get(), hover = in(mx, my, x, y, tw, th);
            g.fill(x, y, x + tw, y + th, on ? 0xFFFFFFFF : hover ? 0x30FFFFFF : 0x20FFFFFF);
            g.outline(x, y, tw, th, 0xFFFFFFFF);
            int c = on ? 0xFF000000 : 0xFFFFFFFF;
            g.text(font, m.name(), x + 6, y + 6, c, false);
            g.text(font, on ? "ON" : "OFF", x + tw - 6 - font.width(on ? "ON" : "OFF"), y + 6, c, false);
            g.text(font, font.plainSubstrByWidth(m.info(), tw - 12), x + 6, y + 20, on ? 0xFF444444 : 0xFF999999, false);
        }
    }

    private void drawCapes(GuiGraphicsExtractor g, int mx, int my) {
        int n = Capes.ALL.length + 1, perRow = 5;
        int tw = (pw - 16 - (perRow - 1) * 6) / perRow, th = 74;
        for (int i = 0; i < n; i++) {
            int x = px + 8 + (i % perRow) * (tw + 6), y = py + 32 + (i / perRow) * (th + 6);
            String id = i == 0 ? "" : Capes.ALL[i - 1].id();
            String name = i == 0 ? "None" : Capes.ALL[i - 1].name();
            boolean on = id.equals(Config.INSTANCE.cape), hover = in(mx, my, x, y, tw, th);
            g.fill(x, y, x + tw, y + th, on ? 0xFFFFFFFF : hover ? 0x30FFFFFF : 0x20FFFFFF);
            g.outline(x, y, tw, th, 0xFFFFFFFF);
            if (i > 0) {
                int[] f = Capes.ALL[i - 1].front();
                int s = 3, cx = x + (tw - 10 * s) / 2, cy = y + 6;
                for (int yy = 0; yy < 16; yy++)
                    for (int xx = 0; xx < 10; xx++)
                        g.fill(cx + xx * s, cy + yy * s, cx + xx * s + s, cy + yy * s + s, 0xFF000000 | f[yy * 10 + xx]);
            } else {
                g.centeredText(font, "-", x + tw / 2, y + 28, on ? 0xFF000000 : 0xFFFFFFFF);
            }
            String label = font.plainSubstrByWidth(name, tw - 4);
            g.text(font, label, x + (tw - font.width(label)) / 2, y + th - 12, on ? 0xFF000000 : 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x(), my = e.y();
        // tabs
        String[] tabs = { "MODS", "CAPES" };
        int tx = px + pw - 8;
        for (int i = tabs.length - 1; i >= 0; i--) {
            int w = font.width(tabs[i]) + 12;
            tx -= w;
            if (in(mx, my, tx, py + 4, w, 16)) { tab = i; return true; }
            tx -= 4;
        }
        if (tab == 0) {
            int tw = tileW(), th = 34;
            for (int i = 0; i < MODS.size(); i++) {
                int x = px + 8 + (i % cols()) * (tw + 6), y = py + 32 + (i / cols()) * (th + 6);
                if (in(mx, my, x, y, tw, th)) {
                    Mod m = MODS.get(i);
                    m.set().accept(!m.get().get());
                    Config.save();
                    return true;
                }
            }
        } else {
            int n = Capes.ALL.length + 1, perRow = 5;
            int tw = (pw - 16 - (perRow - 1) * 6) / perRow, th = 74;
            for (int i = 0; i < n; i++) {
                int x = px + 8 + (i % perRow) * (tw + 6), y = py + 32 + (i / perRow) * (th + 6);
                if (in(mx, my, x, y, tw, th)) {
                    Config.INSTANCE.cape = i == 0 ? "" : Capes.ALL[i - 1].id();
                    Config.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (OlkkuClient.menuKey.matches(e)) { onClose(); return true; }
        return super.keyPressed(e);
    }
}
