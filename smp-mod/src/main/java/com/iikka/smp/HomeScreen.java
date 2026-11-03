package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One home: Teleport, Change Icon, Rename, Delete, Back. */
public class HomeScreen extends Screen {
    private final String name;
    private final String icon;

    public HomeScreen(String name, String icon) {
        super(Component.literal(name));
        this.name = name;
        this.icon = icon;
    }

    @Override
    protected void init() {
        int bw = 140, bh = 20, gap = 4;
        int left = width / 2 - bw - gap / 2, right = width / 2 + gap / 2;
        int y = height / 5;
        addRenderableWidget(Button.builder(Component.literal("Teleport"), b -> SmpClient.command("home " + name))
            .bounds(left, y, bw, bh).build());
        String iconName = HomesScreen.icon(icon).getHoverName().getString();
        addRenderableWidget(Button.builder(Component.literal("Change Icon"), b -> SmpClient.command("homeicon " + name))
            .tooltip(Tooltip.create(Component.literal("Change icon\n").append(Component.literal("Current: " + iconName).withStyle(ChatFormatting.GRAY))))
            .bounds(right, y, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Rename"), b -> minecraft.gui.setScreen(new ChatScreen("/renamehome " + name + " ", false)))
            .bounds(left, y + bh + gap, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED), b -> SmpClient.command("delhome " + name))
            .bounds(right, y + bh + gap, bw, bh).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> SmpClient.command("home"))
            .bounds(width / 2 - bw / 2, y + (bh + gap) * 2, bw, bh).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, width / 2 - font.width(title) / 2, 15, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
