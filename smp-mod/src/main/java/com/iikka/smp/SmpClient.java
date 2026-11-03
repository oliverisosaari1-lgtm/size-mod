package com.iikka.smp;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** The parts that run in the player's game: money box, price tooltips and the menu screens. */
public class SmpClient implements ClientModInitializer {
    private static long money = 0;
    private static boolean show = true;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MoneyPayload.TYPE, (payload, context) -> {
            money = payload.money();
            show = payload.show();
        });

        ClientPlayNetworking.registerGlobalReceiver(ScreenPayload.TYPE, (payload, context) -> {
            Minecraft mc = Minecraft.getInstance();
            switch (payload.screen()) {
                case "menu" -> mc.gui.setScreen(new MenuScreen());
                case "chat" -> mc.gui.setScreen(new ChatScreen(payload.data(), false));
                case "homes" -> {
                    List<String[]> homes = new ArrayList<>();
                    for (String line : payload.data().split("\n")) {
                        if (line.isBlank()) continue;
                        String[] parts = line.split("\\|", 2);
                        homes.add(new String[]{parts[0], parts.length > 1 ? parts[1] : "white_bed"});
                    }
                    mc.gui.setScreen(new HomesScreen(homes, 0));
                }
                case "home" -> {
                    String[] parts = payload.data().split("\\|", 2);
                    mc.gui.setScreen(new HomeScreen(parts[0], parts.length > 1 ? parts[1] : "white_bed"));
                }
                default -> { }
            }
        });

        // "~$ 40" under the item name, like DonutSMP. Skipped for the mod's own menu buttons.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (lines.isEmpty() || stack.isEmpty()) return;
            var lore = stack.get(DataComponents.LORE);
            if (stack.get(DataComponents.CUSTOM_NAME) != null && lore != null && !lore.lines().isEmpty()) return; // menu buttons
            if (lore != null && !lore.lines().isEmpty() && lore.lines().get(0).getString().startsWith("~$")) return; // auction items
            lines.add(1, Prices.priceLine(Prices.price(stack)));
        });

        // Money box on the right: your name, then a green $ and the amount
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("donutsmp", "money"), (graphics, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            // In singleplayer read the money straight from the world, in case the server message didn't arrive
            if (mc.hasSingleplayerServer()) {
                Data.PlayerData pd = SmpMod.DATA.players.get(mc.player.getUUID().toString());
                if (pd != null) { money = pd.money; show = pd.moneyHud; }
            }
            if (!show) return;
            var font = mc.font;
            String name = mc.player.getName().getString();
            String amount = Prices.shortNum(money);
            int pad = 3;
            int w = Math.max(font.width(name), font.width("$ " + amount)) + pad * 2;
            int h = font.lineHeight * 2 + pad * 2 + 1;
            int x = graphics.guiWidth() - w - 2;
            int y = graphics.guiHeight() / 2 - h / 2;
            graphics.fill(x, y, x + w, y + h, 0x90000000);
            graphics.text(font, name, x + pad, y + pad, 0xFFFFFFFF, true);
            int y2 = y + pad + font.lineHeight + 1;
            graphics.text(font, "$", x + pad, y2, 0xFF55FF55, true);
            graphics.text(font, amount, x + pad + font.width("$ "), y2, 0xFFFFFFFF, true);
        });
    }

    /** Runs a command as if the player typed it. */
    static void command(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        if (mc.player != null) mc.player.connection.sendCommand(cmd);
    }
}
