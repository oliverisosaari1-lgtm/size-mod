package com.iikka.practice;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;

import java.io.InputStream;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class PracticeClient implements ClientModInitializer {
    public static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("olkkuspractice", "textures/gui/background.png");
    public static final String WORLD = "Olkku Practice";
    public static KeyMapping zoom;
    static StartPayload pending;
    static boolean hostLan;

    private static String key(AbstractWidget w) {
        return w.getMessage().getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    static boolean essentialLoaded() {
        var l = FabricLoader.getInstance();
        return l.isModLoaded("essential") || l.isModLoaded("essential-container") || l.isModLoaded("essential-loader");
    }

    /** Copies the practice world into saves (first time) and opens it. */
    static void openPracticeWorld(Minecraft mc, StartPayload what, boolean lan) {
        try {
            Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
            if (!Files.exists(dir.resolve("level.dat"))) {
                Files.createDirectories(dir);
                try (InputStream in = PracticeClient.class.getResourceAsStream("/assets/olkkuspractice/practice_world.zip");
                     ZipInputStream zip = new ZipInputStream(in)) {
                    ZipEntry e;
                    while ((e = zip.getNextEntry()) != null) {
                        Path out = dir.resolve(e.getName()).normalize();
                        if (!out.startsWith(dir)) continue;
                        if (e.isDirectory()) Files.createDirectories(out);
                        else { Files.createDirectories(out.getParent()); Files.copy(zip, out, StandardCopyOption.REPLACE_EXISTING); }
                    }
                }
            }
            pending = what;
            hostLan = lan;
            mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
        } catch (Exception ex) {
            mc.setScreen(new TitleScreen());
        }
    }

    @Override
    public void onInitializeClient() {
        KeyMapping menu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.olkkuspractice.menu", InputConstants.KEY_K, KeyMapping.Category.MISC));
        zoom = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.olkkuspractice.zoom", InputConstants.KEY_C, KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menu.consumeClick()) if (mc.player != null && ClientPlayNetworking.canSend(MenuPayload.TYPE)) ClientPlayNetworking.send(new MenuPayload());
        });

        // After the practice world loads, start what was picked in the main menu
        ClientPlayConnectionEvents.JOIN.register((h, sender, mc) -> mc.execute(() -> {
            StartPayload p = pending;
            pending = null;
            if (p == null) return;
            if (hostLan && mc.getSingleplayerServer() != null && !mc.getSingleplayerServer().isPublished()) {
                int port = net.minecraft.util.HttpUtil.getAvailablePort();
                if (mc.getSingleplayerServer().publishServer(net.minecraft.world.level.GameType.SURVIVAL, false, port) && mc.player != null)
                    mc.player.displayClientMessage(Component.literal("Your 1v1 world is open! Friends on your network can join it (port " + port + ").").withStyle(ChatFormatting.GOLD), false);
            }
            if (ClientPlayNetworking.canSend(StartPayload.TYPE)) ClientPlayNetworking.send(p);
        }));

        // Main menu: Play Multiplayer + Bot Practice, no Singleplayer/Multiplayer/Realms, red buttons
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                var buttons = Screens.getButtons(screen);
                AbstractWidget sp = null, mp = null, realms = null;
                for (AbstractWidget b : buttons) {
                    switch (key(b)) {
                        case "menu.singleplayer" -> sp = b;
                        case "menu.multiplayer" -> mp = b;
                        case "menu.online" -> realms = b;
                        default -> { }
                    }
                }
                int bx = sp != null ? sp.getX() : w / 2 - 100, by = sp != null ? sp.getY() : h / 4 + 48, bw = sp != null ? sp.getWidth() : 200;
                for (AbstractWidget b : new AbstractWidget[]{sp, mp, realms}) if (b != null) { b.visible = false; b.active = false; }
                buttons.add(Button.builder(Component.literal("Play Multiplayer"), b -> mc.setScreen(new PracticeScreens.DuelScreen(screen))).bounds(bx, by, bw, 20).build());
                buttons.add(Button.builder(Component.literal("Bot Practice"), b -> mc.setScreen(new PracticeScreens.BotScreen(screen))).bounds(bx, by + 24, bw, 20).build());

                ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                    for (AbstractWidget b : Screens.getButtons(s)) {
                        if (!b.visible) continue;
                        int c = b.isHoveredOrFocused() ? 0x99FF2020 : 0x88B00000;
                        g.fill(b.getX() + 1, b.getY() + 1, b.getX() + b.getWidth() - 1, b.getY() + b.getHeight() - 1, c);
                        if (!b.getMessage().getString().isEmpty())
                            g.drawCenteredString(mc.font, b.getMessage(), b.getX() + b.getWidth() / 2, b.getY() + (b.getHeight() - 8) / 2, 0xFFFFFFFF);
                    }
                    g.drawString(mc.font, Component.literal("Olkku's Practice").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), 4, 4, 0xFFFFFFFF);
                    if (!essentialLoaded())
                        g.drawCenteredString(mc.font, Component.literal("Olkku's Practice needs the Essential mod! Put it in your mods folder.").withStyle(ChatFormatting.YELLOW), s.width / 2, 16, 0xFFFFFF55);
                });
            }
            // Options: "Done Settings" sets good PvP settings in one click
            if (screen instanceof OptionsScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Done Settings").withStyle(ChatFormatting.RED), b -> {
                    PracticeScreens.applyDoneSettings(mc);
                    b.setMessage(Component.literal("Done Settings: applied!").withStyle(ChatFormatting.GREEN));
                }).bounds(w - 124, 4, 120, 20).build());
            }
        });

        // Small FPS / ping display
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("olkkuspractice", "hud"), (g, tick) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
            String text = mc.getFps() + " FPS" + (info != null ? "  " + info.getLatency() + " ms" : "");
            g.drawString(mc.font, text, 4, 4, 0xFFFF5555);
        });
    }
}
