package com.iikka.practice;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

import java.util.List;

/** Main menu screens: Bot Practice and Play Multiplayer. */
public class PracticeScreens {
    static int kit = 4, diff = 1, loot = 2, arena = 0;

    static String up(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static Button cycle(int x, int y, String label, List<String> opts, int[] idx, java.util.function.Function<String, String> pretty) {
        return Button.builder(Component.literal(label + ": " + pretty.apply(opts.get(idx[0]))), b -> {
            idx[0] = (idx[0] + 1) % opts.size();
            b.setMessage(Component.literal(label + ": " + pretty.apply(opts.get(idx[0]))));
        }).bounds(x, y, 200, 20).build();
    }

    public static class BotScreen extends Screen {
        private final Screen parent;
        private final int[] k = {kit}, d = {diff}, l = {loot}, a = {arena};
        public BotScreen(Screen parent) { super(Component.literal("Bot Practice")); this.parent = parent; }

        @Override
        protected void init() {
            int x = width / 2 - 100, y = height / 4;
            addRenderableWidget(cycle(x, y, "Kit", Kits.NAMES, k, Kits::pretty));
            addRenderableWidget(cycle(x, y + 24, "Bot skill", Practice.DIFFICULTY, d, PracticeScreens::up));
            addRenderableWidget(cycle(x, y + 48, "Bot loot", Practice.LOOT, l, PracticeScreens::up));
            addRenderableWidget(cycle(x, y + 72, "Arena", Arenas.NAMES, a, Arenas::pretty));
            addRenderableWidget(Button.builder(Component.literal("Start!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), b -> {
                kit = k[0]; diff = d[0]; loot = l[0]; arena = a[0];
                PracticeClient.openPracticeWorld(minecraft, new StartPayload("bot", Kits.NAMES.get(kit), Practice.DIFFICULTY.get(diff), Practice.LOOT.get(loot), Arenas.NAMES.get(arena)), false);
            }).bounds(x, y + 104, 200, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(x, y + 128, 200, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            super.render(g, mx, my, delta);
            g.drawCenteredString(font, Component.literal("Bot Practice").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), width / 2, height / 4 - 24, 0xFFFFFFFF);
            g.drawCenteredString(font, "Click a button to change it. Press K in game for more.", width / 2, height / 4 - 12, 0xFFAAAAAA);
        }
    }

    public static class DuelScreen extends Screen {
        private final Screen parent;
        private final int[] a = {arena};
        public DuelScreen(Screen parent) { super(Component.literal("Play Multiplayer")); this.parent = parent; }

        @Override
        protected void init() {
            int x = width / 2 - 100, y = height / 4;
            addRenderableWidget(cycle(x, y, "Arena", Arenas.NAMES, a, Arenas::pretty));
            addRenderableWidget(Button.builder(Component.literal("Start 1v1 (host)").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), b -> {
                arena = a[0];
                PracticeClient.openPracticeWorld(minecraft, new StartPayload("duel", "", "", "", Arenas.NAMES.get(arena)), true);
            }).bounds(x, y + 32, 200, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Join a friend"), b -> minecraft.setScreen(new JoinMultiplayerScreen(this))).bounds(x, y + 56, 200, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Back"), b -> minecraft.setScreen(parent)).bounds(x, y + 88, 200, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float delta) {
            super.render(g, mx, my, delta);
            g.drawCenteredString(font, Component.literal("Play Multiplayer").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), width / 2, height / 4 - 24, 0xFFFFFFFF);
            g.drawCenteredString(font, "Pick an arena. You fight the second player in the world.", width / 2, height / 4 - 12, 0xFFAAAAAA);
        }
    }

    /** Good default settings for PvP. */
    static void applyDoneSettings(Minecraft mc) {
        var o = mc.options;
        o.fov().set(90);
        o.gamma().set(1.0);
        o.bobView().set(false);
        o.autoJump().set(false);
        o.particles().set(ParticleStatus.DECREASED);
        o.renderDistance().set(8);
        o.simulationDistance().set(8);
        o.entityShadows().set(false);
        o.toggleSprint().set(true);
        o.framerateLimit().set(260);
        o.enableVsync().set(false);
        o.save();
    }
}
