package com.iikka.practice;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.*;
import java.util.function.Consumer;

/** Simple click-a-button chest menus, and the kit editor. */
public class Menus {
    public record Button(ItemStack icon, Consumer<ServerPlayer> action) { }

    public static ItemStack icon(Item item, String name, ChatFormatting color, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withItalic(false).withColor(color)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Component.literal(l).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)));
        s.set(DataComponents.LORE, new ItemLore(lines));
        return s;
    }

    public static void open(ServerPlayer p, String title, Map<Integer, Button> buttons) {
        int rows = Math.max(1, Math.min(6, (buttons.keySet().stream().max(Integer::compare).orElse(0) / 9) + 1));
        MenuType<?> type = switch (rows) { case 1 -> MenuType.GENERIC_9x1; case 2 -> MenuType.GENERIC_9x2; case 3 -> MenuType.GENERIC_9x3; case 4 -> MenuType.GENERIC_9x4; case 5 -> MenuType.GENERIC_9x5; default -> MenuType.GENERIC_9x6; };
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ButtonMenu(type, id, inv, rows, buttons), Component.literal(title)));
    }

    static class ButtonMenu extends ChestMenu {
        private final Map<Integer, Button> buttons;
        ButtonMenu(MenuType<?> type, int id, Inventory inv, int rows, Map<Integer, Button> buttons) {
            super(type, id, inv, new SimpleContainer(rows * 9), rows);
            this.buttons = buttons;
            for (var e : buttons.entrySet()) getContainer().setItem(e.getKey(), e.getValue().icon());
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player pl) {
            if (!(pl instanceof ServerPlayer p)) return;
            Button b = buttons.get(slot);
            if (b != null) { p.closeContainer(); b.action().accept(p); }
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    // ---------- Kit editor ----------
    private static final int RESET = 45, SAVE = 49, CANCEL = 53;

    public static void editKit(ServerPlayer p, String kit) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new KitEditor(id, inv, (ServerPlayer) pl, kit),
            Component.literal("Edit kit: " + Kits.pretty(kit))));
    }

    /** chest slot -> kit slot. Rows 1-3: inventory, row 4: hotbar, row 5: boots, leggings, chest, helmet, offhand. */
    private static int kitSlot(int chest) {
        if (chest < 27) return chest + 9;
        if (chest < 36) return chest - 27;
        if (chest < 41) return chest; // 36-40 armor + offhand
        return -1;
    }

    static class KitEditor extends ChestMenu {
        private final String kit;
        private final ServerPlayer owner;

        KitEditor(int id, Inventory inv, ServerPlayer p, String kit) {
            super(MenuType.GENERIC_9x6, id, inv, new SimpleContainer(54), 6);
            this.kit = kit;
            this.owner = p;
            fill(Kits.get(p, kit));
        }

        private void fill(ItemStack[] k) {
            var c = getContainer();
            for (int i = 0; i < 54; i++) {
                int ks = kitSlot(i);
                c.setItem(i, ks >= 0 ? k[ks] : icon(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.GRAY));
            }
            c.setItem(41, icon(Items.IRON_CHESTPLATE, "<- Boots, Leggings, Chestplate, Helmet, Offhand", ChatFormatting.YELLOW));
            c.setItem(RESET, icon(Items.BARRIER, "Reset to default", ChatFormatting.RED));
            c.setItem(SAVE, icon(Items.LIME_CONCRETE, "Save kit", ChatFormatting.GREEN, "Move items around, then click here."));
            c.setItem(CANCEL, icon(Items.RED_CONCRETE, "Cancel", ChatFormatting.RED));
        }

        @Override
        public void clicked(int slot, int button, ClickType type, Player pl) {
            if (!(pl instanceof ServerPlayer p)) return;
            if (slot >= 0 && slot < 54 && kitSlot(slot) < 0) {
                if (slot == SAVE) {
                    ItemStack[] k = new ItemStack[Kits.SIZE];
                    for (int i = 0; i < 41; i++) k[kitSlot(i)] = getContainer().getItem(i).copy();
                    Kits.set(p, kit, k);
                    p.displayClientMessage(Component.literal("Kit " + Kits.pretty(kit) + " saved!").withStyle(ChatFormatting.GREEN), false);
                    clearAndClose(p);
                } else if (slot == RESET) {
                    Kits.reset(p, kit);
                    fill(Kits.get(p, kit));
                    broadcastChanges();
                    p.displayClientMessage(Component.literal("Kit " + Kits.pretty(kit) + " reset to default.").withStyle(ChatFormatting.YELLOW), false);
                } else if (slot == CANCEL) clearAndClose(p);
                return;
            }
            super.clicked(slot, button, type, pl);
        }

        private void clearAndClose(ServerPlayer p) {
            for (int i = 0; i < 54; i++) getContainer().setItem(i, ItemStack.EMPTY); // items in the editor don't drop out
            p.closeContainer();
        }

        @Override
        public void removed(Player player) {
            for (int i = 0; i < 54; i++) getContainer().setItem(i, ItemStack.EMPTY);
            super.removed(player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }
}
