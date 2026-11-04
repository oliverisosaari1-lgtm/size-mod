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
    private static long shards = 0;

    private static net.minecraft.client.KeyMapping zoomKey;

    private static float currentZoom = 4;
    private static boolean wasZooming = false;

    public static boolean zooming() {
        boolean z = zoomKey != null && zoomKey.isDown() && Minecraft.getInstance().gui.screen() == null;
        if (z && !wasZooming) currentZoom = Math.max(1, ClientConfig.INSTANCE.zoom); // start from the zoom in settings
        wasZooming = z;
        return z;
    }

    public static float zoomAmount() {
        return currentZoom;
    }

    /** Scroll up = zoom in more, scroll down = zoom out. Between 1.5x and 50x. */
    public static void scrollZoom(double amount) {
        if (amount == 0) return;
        currentZoom *= amount > 0 ? 1.2f : 1 / 1.2f;
        currentZoom = Math.max(1.5f, Math.min(50f, currentZoom));
    }

    /** The first time the game starts with this mod: VSync off, Quake Pro FOV, unlimited FPS, GUI scale 3, distances 7. */
    private static void applyGameSettings(Minecraft mc) {
        ClientConfig c = ClientConfig.INSTANCE;
        if (c.appliedGameSettings) return;
        var o = mc.options;
        o.enableVsync().set(false);
        o.fov().set(110);            // Quake Pro
        o.framerateLimit().set(260); // 260 = Unlimited
        o.guiScale().set(3);
        o.renderDistance().set(7);
        o.simulationDistance().set(7);
        o.save();
        mc.resizeGui();
        c.appliedGameSettings = true;
        c.save();
    }

    @Override
    public void onInitializeClient() {
        // Sodium is only needed in the game, not on the server, so we check for it here
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("sodium")) {
            throw new IllegalStateException("DonutSMP Mod needs Sodium. Add Sodium to your mods folder.");
        }
        // Light blue inventory and chests, always on
        net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("donutsmp").ifPresent(mod ->
            net.fabricmc.fabric.api.resource.v1.ResourceLoader.registerBuiltinPack(
                Identifier.fromNamespaceAndPath("donutsmp", "lightblue"), "resourcepacks/lightblue", mod,
                net.minecraft.network.chat.Component.literal("DonutSMP Light Blue"),
                net.fabricmc.fabric.api.resource.v1.pack.PackActivationType.ALWAYS_ENABLED));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(SmpClient::applyGameSettings);
        var category = net.minecraft.client.KeyMapping.Category.register(Identifier.fromNamespaceAndPath("donutsmp", "keys"));
        zoomKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(
            new net.minecraft.client.KeyMapping("key.donutsmp.zoom", com.mojang.blaze3d.platform.InputConstants.KEY_C, category));

        // "DonutSMP" button on the main menu, under the other buttons
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.TitleScreen)) return;
            var widgets = net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen);
            int bottom = h / 4 + 48;
            for (var wd : widgets) {
                if (wd.getWidth() >= 98 && Math.abs(wd.getX() + wd.getWidth() / 2 - w / 2) < 110) bottom = Math.max(bottom, wd.getY() + wd.getHeight());
            }
            widgets.add(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("DonutSMP"),
                b -> client.gui.setScreen(new DonutSmpScreen(screen))).bounds(w / 2 - 100, bottom + 4, 200, 20).build());
        });

        ClientPlayNetworking.registerGlobalReceiver(MoneyPayload.TYPE, (payload, context) -> {
            money = payload.money();
            show = payload.show();
            shards = payload.shards();
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
            if (!ClientConfig.INSTANCE.priceTooltips || lines.isEmpty() || stack.isEmpty()) return;
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
                if (pd != null) { money = pd.money; show = pd.moneyHud; shards = pd.shards; }
            }
            if (!show || !ClientConfig.INSTANCE.moneyBox) return;
            var font = mc.font;
            String name = mc.player.getName().getString();
            String amount = Prices.shortNum(money);
            int pad = 3;
            String shardText = Prices.shortNum(shards);
            int w = Math.max(font.width(name), Math.max(font.width("$ " + amount), font.width("◆ " + shardText))) + pad * 2;
            int h = font.lineHeight * 3 + pad * 2 + 2;
            int x = graphics.guiWidth() - w - 2;
            int y = graphics.guiHeight() / 2 - h / 2;
            graphics.fill(x, y, x + w, y + h, 0x90000000);
            graphics.text(font, name, x + pad, y + pad, 0xFFFFFFFF, true);
            int y2 = y + pad + font.lineHeight + 1;
            graphics.text(font, "$", x + pad, y2, 0xFF55FF55, true);
            graphics.text(font, amount, x + pad + font.width("$ "), y2, 0xFFFFFFFF, true);
            int y3 = y2 + font.lineHeight + 1;
            graphics.text(font, "◆", x + pad, y3, 0xFFDD55FF, true);
            graphics.text(font, shardText, x + pad + font.width("◆ "), y3, 0xFFFFFFFF, true);
        });
    }

    /** Runs a command as if the player typed it. */
    static void command(String cmd) {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        if (mc.player != null) mc.player.connection.sendCommand(cmd);
    }
}
