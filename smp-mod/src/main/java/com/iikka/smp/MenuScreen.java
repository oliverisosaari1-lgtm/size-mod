package com.iikka.smp;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** /settings: two columns of buttons that open each feature. */
public class MenuScreen extends Screen {
    private static final String[][] LEFT = {
        {"Homes", "home"}, {"Auction", "ah"}, {"Quick Buy", "quickbuy"}, {"Orders", "orders"},
        {"Sell", "sell"}, {"Teleport", ">/tpa "}, {"Leaderboards", "baltop"}};
    private static final String[][] RIGHT = {
        {"RTP", "rtp"}, {"RTP Queue", "rtpqueue"}, {"Friends", "friends"}, {"Shard Shop", "shards"},
        {"Pay", ">/pay "}, {"Stats", "stats"}, {"Settings", "togglesettings"}};

    public MenuScreen() {
        super(Component.literal("Menu"));
    }

    @Override
    protected void init() {
        int bw = 160, bh = 20, gap = 2;
        int total = LEFT.length * (bh + gap);
        int top = height / 2 - total / 2;
        int left = width / 2 - bw - gap;
        for (int i = 0; i < LEFT.length; i++) add(LEFT[i], left, top + i * (bh + gap), bw, bh);
        for (int i = 0; i < RIGHT.length; i++) add(RIGHT[i], width / 2 + gap, top + i * (bh + gap), bw, bh);
    }

    private void add(String[] b, int x, int y, int w, int h) {
        addRenderableWidget(Button.builder(Component.literal(b[0]), btn -> {
            if (b[1].startsWith(">")) minecraft.setScreen(new ChatScreen(b[1].substring(1), false));
            else SmpClient.command(b[1]);
        }).bounds(x, y, w, h).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
