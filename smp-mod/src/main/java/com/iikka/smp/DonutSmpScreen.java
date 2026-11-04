package com.iikka.smp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The "DonutSMP" button on the main menu: settings for the mod and a list of commands. */
public class DonutSmpScreen extends Screen {
    private static final int[] ZOOMS = {2, 4, 8, 16};
    private static final String[] HELP = {
        "/ah - Auction    /sell - Sell items    /pay - Pay a player",
        "/home - Your homes    /sethome name - Save a home",
        "/rtp - Random teleport    /spawn - Spawn    /lobby - Lobby",
        "/tpa, /tpahere, /tpaccept - Teleport to friends",
        "/bal, /baltop, /stats, /shards    /settings - Menu",
        "You get 1 shard for every 2 hours you play",
        "Hold C to zoom"};
    private final Screen parent;

    public DonutSmpScreen(Screen parent) {
        super(Component.literal("DonutSMP"));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? "ON" : "OFF";
    }

    @Override
    protected void init() {
        ClientConfig c = ClientConfig.INSTANCE;
        int bw = 200, x = width / 2 - bw / 2, y = 40;
        addRenderableWidget(Button.builder(Component.literal("Money box: " + onOff(c.moneyBox)), b -> {
            c.moneyBox = !c.moneyBox; c.save(); b.setMessage(Component.literal("Money box: " + onOff(c.moneyBox)));
        }).bounds(x, y, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Item prices: " + onOff(c.priceTooltips)), b -> {
            c.priceTooltips = !c.priceTooltips; c.save(); b.setMessage(Component.literal("Item prices: " + onOff(c.priceTooltips)));
        }).bounds(x, y + 24, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Zoom: " + c.zoom + "x"), b -> {
            int i = 0;
            while (i < ZOOMS.length && ZOOMS[i] != c.zoom) i++;
            c.zoom = ZOOMS[(i + 1) % ZOOMS.length]; c.save(); b.setMessage(Component.literal("Zoom: " + c.zoom + "x"));
        }).bounds(x, y + 48, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x, height - 30, bw, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, width / 2 - font.width(title) / 2, 15, 0xFFFFFFFF, true);
        int y = 40 + 80;
        for (String line : HELP) {
            graphics.text(font, line, width / 2 - font.width(line) / 2, y, 0xFFCCCCCC, true);
            y += font.lineHeight + 3;
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
