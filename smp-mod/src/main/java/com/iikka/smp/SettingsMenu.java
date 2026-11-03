package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** /settings: click a dye to switch a setting on or off. */
public class SettingsMenu extends ChestMenu {
    private final SimpleContainer box;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new SettingsMenu(id, inv, new SimpleContainer(27), (ServerPlayer) p),
            Component.literal("DonutSMP Mod Settings")));
    }

    private SettingsMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x3, id, inv, box, 3);
        this.box = box;
        fill(player);
    }

    private static ItemStack toggle(String name, boolean on, String info) {
        return Gui.button(on ? Items.LIME_DYE : Items.GRAY_DYE, name + ": " + (on ? "ON" : "OFF"),
            on ? ChatFormatting.GREEN : ChatFormatting.RED, info, "Click to switch");
    }

    private void fill(ServerPlayer player) {
        Data.PlayerData d = SmpMod.data(player);
        for (int i = 0; i < 27; i++) box.setItem(i, Gui.filler());
        box.setItem(11, toggle("Money on screen", SmpMod.DATA.moneySidebar, "Shows everyone's money on the right"));
        box.setItem(13, toggle("TPA requests", d.tpaRequests, "Let other players send you /tpa"));
        box.setItem(15, toggle("Combat timer", d.combatTimer, "Show the combat countdown"));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        if (slot >= 0 && slot < 27) {
            Data.PlayerData d = SmpMod.data(player);
            if (slot == 11) { SmpMod.DATA.moneySidebar = !SmpMod.DATA.moneySidebar; SmpMod.updateSidebar(); }
            if (slot == 13) d.tpaRequests = !d.tpaRequests;
            if (slot == 15) d.combatTimer = !d.combatTimer;
            fill(player);
            sendAllDataToRemote();
            return;
        }
        if (type == ClickType.QUICK_MOVE) return;
        super.clicked(slot, button, type, p);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
