package com.iikka.smp;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /home: a row of your homes. Click one to teleport, rename, change its icon or delete it. */
public class HomesScreen extends Screen {
    private static final int PER_PAGE = 3;
    private final List<String[]> homes; // {name, icon}
    private final int page;
    private final List<Object[]> icons = new ArrayList<>(); // {ItemStack, x, y}

    public HomesScreen(List<String[]> homes, int page) {
        super(Component.literal("Homes"));
        this.homes = homes;
        this.page = page;
    }

    static ItemStack icon(String id) {
        try {
            return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)));
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    protected void init() {
        icons.clear();
        int start = page * PER_PAGE;
        int shown = Math.min(PER_PAGE, homes.size() - start);
        boolean more = homes.size() > PER_PAGE;
        int count = shown + (more ? 1 : 0);
        int bw = 64, gap = 4;
        int x = width / 2 - (count * bw + (count - 1) * gap) / 2;
        int y = height / 5;
        for (int i = 0; i < shown; i++) {
            String[] h = homes.get(start + i);
            addRenderableWidget(Button.builder(Component.literal("   " + h[0]), b -> minecraft.setScreen(new HomeScreen(h[0], h[1])))
                .bounds(x, y, bw, 20).build());
            icons.add(new Object[]{icon(h[1]), x + 4, y + 2});
            x += bw + gap;
        }
        if (more) {
            int next = (start + PER_PAGE < homes.size()) ? page + 1 : 0;
            addRenderableWidget(Button.builder(Component.literal("Show More"), b -> minecraft.setScreen(new HomesScreen(homes, next)))
                .bounds(x, y, bw, 20).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.text(font, title, width / 2 - font.width(title) / 2, 15, 0xFFFFFFFF, true);
        for (Object[] ic : icons) graphics.item((ItemStack) ic[0], (int) ic[1], (int) ic[2]);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
