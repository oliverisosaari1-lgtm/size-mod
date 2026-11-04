package com.iikka.pvp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** The PvP menu. */
public class PvpScreen extends Screen {
    public PvpScreen() {
        super(Component.literal("PvP Menu"));
    }

    private class Slider extends AbstractSliderButton {
        final String name; final double min, max; final DoubleConsumer set; final int decimals;
        Slider(int x, int y, String name, double min, double max, DoubleSupplier get, DoubleConsumer set, int decimals) {
            super(x, y, 200, 20, Component.empty(), (get.getAsDouble() - min) / (max - min));
            this.name = name; this.min = min; this.max = max; this.set = set; this.decimals = decimals;
            updateMessage();
        }
        double real() { return min + value * (max - min); }
        @Override protected void updateMessage() {
            setMessage(Component.literal(name + ": " + String.format("%." + decimals + "f", real())));
        }
        @Override protected void applyValue() {
            double v = real();
            double step = decimals == 0 ? 1 : Math.pow(10, -decimals);
            set.accept(Math.round(v / step) * step);
            Config.save();
        }
    }

    @Override
    protected void init() {
        Config c = Config.INSTANCE;
        int x = width / 2 - 100, y = 50;
        addRenderableWidget(new Slider(x, y, "Hand X (left/right)", -1, 1, () -> c.x, v -> c.x = v, 2));
        addRenderableWidget(new Slider(x, y += 24, "Hand Y (up/down)", -1, 1, () -> c.y, v -> c.y = v, 2));
        addRenderableWidget(new Slider(x, y += 24, "Hand Z (near/far)", -1, 1, () -> c.z, v -> c.z = v, 2));
        addRenderableWidget(new Slider(x, y += 24, "Size", 0.1, 2, () -> c.scale, v -> c.scale = v, 2));
        addRenderableWidget(new Slider(x, y += 24, "Turn X", -180, 180, () -> c.rotX, v -> c.rotX = v, 0));
        addRenderableWidget(new Slider(x, y += 24, "Turn Y", -180, 180, () -> c.rotY, v -> c.rotY = v, 0));
        addRenderableWidget(new Slider(x, y += 24, "Turn Z", -180, 180, () -> c.rotZ, v -> c.rotZ = v, 0));
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> {
            Config.INSTANCE = new Config();
            Config.save();
            rebuildWidgets();
        }).bounds(x, y += 30, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + 102, y, 98, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawCenteredString(font, "PvP Menu - Hand Viewmodel", width / 2, 25, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false; // so you can see your hand change while you move the sliders
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // no dark background, so you can see your hand
    }
}
