package com.iikka.smp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Draws the money box on the right side of the screen. */
public class SmpClient implements ClientModInitializer {
    private static long money = 0;
    private static boolean show = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MoneyPayload.TYPE, (payload, context) -> {
            money = payload.money();
            show = payload.show();
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("donutsmp", "money"), (graphics, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (!show || mc.player == null || mc.options.hideGui) return;
            var font = mc.font;
            String name = mc.player.getName().getString();
            String amount = Prices.money(money).substring(1); // "31M"
            int pad = 3;
            int w = Math.max(font.width(name), font.width("$ " + amount)) + pad * 2;
            int h = font.lineHeight * 2 + pad * 2 + 1;
            int x = graphics.guiWidth() - w - 2;
            int y = graphics.guiHeight() / 2 - h / 2;
            graphics.fill(x, y, x + w, y + h, 0x90000000);
            graphics.drawString(font, name, x + pad, y + pad, 0xFFFFFFFF, true);
            int y2 = y + pad + font.lineHeight + 1;
            graphics.drawString(font, "$", x + pad, y2, 0xFF55FF55, true);
            graphics.drawString(font, amount, x + pad + font.width("$ "), y2, 0xFFFFFFFF, true);
        });
    }
}
