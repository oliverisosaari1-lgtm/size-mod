package com.iikka.olkkuclient;

import com.iikka.olkkuclient.mixin.KeyMappingAccess;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;

public class OlkkuClient implements ClientModInitializer {
    public static KeyMapping menuKey, zoomKey;
    private static final ArrayDeque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();

    public static int cps(boolean leftButton) {
        ArrayDeque<Long> q = leftButton ? left : right;
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.pollFirst();
        return q.size();
    }

    public static boolean zooming() {
        Minecraft mc = Minecraft.getInstance();
        return Config.INSTANCE.zoom && zoomKey != null && zoomKey.isDown() && mc.gui.screen() == null;
    }

    @Override
    public void onInitializeClient() {
        Config.load();
        var cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("olkkuclient", "keys"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.olkkuclient.menu", InputConstants.KEY_RSHIFT, cat));
        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.olkkuclient.zoom", InputConstants.KEY_C, cat));

        // Count clicks before the game uses them up.
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            long now = System.currentTimeMillis();
            int l = ((KeyMappingAccess) mc.options.keyAttack).olkku$clicks();
            int r = ((KeyMappingAccess) mc.options.keyUse).olkku$clicks();
            for (int i = 0; i < l; i++) left.add(now);
            for (int i = 0; i < r; i++) right.add(now);
        });
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.consumeClick()) if (mc.gui.screen() == null) mc.gui.setScreen(new ClientMenu());
            var p = mc.player;
            if (p != null && Config.INSTANCE.autoSprint && mc.gui.screen() == null && p.input != null
                && p.input.getMoveVector().y > 0.5f && !p.isShiftKeyDown() && !p.isUsingItem()
                && p.getFoodData().getFoodLevel() > 6 && !p.isSprinting())
                p.setSprinting(true);
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("olkkuclient", "hud"), (g, tick) -> Hud.draw(g));
    }
}
