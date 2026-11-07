package com.iikka.move;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** The F menu: a ring of emotes around a Stop button. */
public class EmoteScreen extends Screen {
    private static final String[] NAMES = { "Sit", "Lay", "Crawl", "Wave", "Dance", "Clap", "Bow", "Cheer", "Cat Dance" };
    private static final byte[] KINDS = { Anim.SIT, Anim.LAY, Anim.CRAWL, Anim.WAVE, Anim.DANCE, Anim.CLAP, Anim.BOW, Anim.CHEER, Anim.CAT };

    public EmoteScreen() { super(Component.literal("Emotes")); }

    @Override
    protected void init() {
        int cx = width / 2, cy = height / 2, r = Math.min(100, height / 3);
        for (int i = 0; i < NAMES.length; i++) {
            double a = -Math.PI / 2 + i * 2 * Math.PI / NAMES.length;
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            byte kind = KINDS[i];
            addRenderableWidget(Button.builder(Component.literal(NAMES[i]), b -> pick(kind)).bounds(x - 32, y - 10, 64, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Stop"), b -> pick(Anim.STOP)).bounds(cx - 25, cy - 10, 50, 20).build());
    }

    private void pick(byte kind) {
        onClose();
        MoveClient.doEmote(kind);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        g.centeredText(font, "Pick an emote (F to close)", width / 2, height / 2 - Math.min(100, height / 3) - 28, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
