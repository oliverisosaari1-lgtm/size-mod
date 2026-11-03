package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** /sell: 6 rows to put items in. The green slot in the corner shows the total and sells everything. */
public class SellMenu extends ChestMenu {
    private static final int INFO = 49;
    private static final int CONFIRM = 53;
    private final SimpleContainer box;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new SellMenu(id, inv, new SimpleContainer(54)),
            Component.literal("Sell")));
    }

    private SellMenu(int id, Inventory inv, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        updateButtons();
    }

    private static boolean isButton(int slot) {
        return slot == INFO || slot == CONFIRM;
    }

    private long total() {
        long sum = 0;
        for (int i = 0; i < 54; i++) {
            if (isButton(i)) continue;
            ItemStack s = box.getItem(i);
            if (!s.isEmpty()) sum += Prices.price(s);
        }
        return sum;
    }

    private void updateButtons() {
        box.setItem(INFO, Gui.withLore(new ItemStack(Items.STAINED_GLASS_PANE.lightGray()),
            Gui.text("Sell", ChatFormatting.WHITE), List.of(Gui.text("Put the items you want to sell here", ChatFormatting.GRAY))));
        box.setItem(CONFIRM, Gui.withLore(new ItemStack(Items.STAINED_GLASS_PANE.lime()),
            Prices.priceLine(total()), List.of(Gui.text("Click to sell items", ChatFormatting.GRAY))));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (slot >= 0 && slot < 54 && isButton(slot)) {
            if (slot == CONFIRM && p instanceof ServerPlayer player) sell(player);
            updateButtons();
            sendAllDataToRemote();
            return;
        }
        super.clicked(slot, button, type, p);
        updateButtons();
        broadcastChanges();
    }

    private void sell(ServerPlayer player) {
        long sum = total();
        if (sum <= 0) {
            player.sendSystemMessage(Component.literal("Put some items in first!").withStyle(ChatFormatting.RED));
            return;
        }
        for (int i = 0; i < 54; i++) if (!isButton(i)) box.setItem(i, ItemStack.EMPTY);
        SmpMod.addMoney(player, sum);
        player.sendSystemMessage(Component.literal("Sold for " + Prices.money(sum) + "! You now have "
            + Prices.money(SmpMod.money(player))).withStyle(ChatFormatting.GREEN));
        player.closeContainer();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || (index < 54 && isButton(index))) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 54) {
            if (!moveItemStackTo(stack, 54, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = moveItemStackTo(stack, 0, INFO, false);
            if (!stack.isEmpty()) moved |= moveItemStackTo(stack, INFO + 1, CONFIRM, false);
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (int i = 0; i < 54; i++) {
            ItemStack s = box.removeItemNoUpdate(i);
            if (isButton(i)) continue;
            if (!s.isEmpty() && player instanceof ServerPlayer sp) Gui.give(sp, s);
        }
    }
}
