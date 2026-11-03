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

/** /sell: put items in the top 5 rows, press the green button. Anything left is given back when you close it. */
public class SellMenu extends ChestMenu {
    private static final int ITEM_SLOTS = 45;
    private static final int CONFIRM = 49;
    private final SimpleContainer box;
    private boolean sold = false;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new SellMenu(id, inv, new SimpleContainer(54)),
            Component.literal("Sell items")));
    }

    private SellMenu(int id, Inventory inv, SimpleContainer box) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        updateButtons();
    }

    private long total() {
        long sum = 0;
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack s = box.getItem(i);
            if (!s.isEmpty()) sum += Prices.price(s);
        }
        return sum;
    }

    private void updateButtons() {
        for (int i = ITEM_SLOTS; i < 54; i++) box.setItem(i, Gui.filler());
        box.setItem(CONFIRM, Gui.button(Items.EMERALD_BLOCK, "Confirm sell", ChatFormatting.GREEN,
            "You get: " + Prices.money(total())));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (slot >= ITEM_SLOTS && slot < 54) {
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
        for (int i = 0; i < ITEM_SLOTS; i++) box.setItem(i, ItemStack.EMPTY);
        SmpMod.addMoney(player, sum);
        player.sendSystemMessage(Component.literal("Sold for " + Prices.money(sum) + "! You now have "
            + Prices.money(SmpMod.money(player))).withStyle(ChatFormatting.GREEN));
        sold = true;
        player.closeContainer();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < ITEM_SLOTS) {
            if (!moveItemStackTo(stack, 54, slots.size(), true)) return ItemStack.EMPTY;
        } else if (index >= 54) {
            if (!moveItemStackTo(stack, 0, ITEM_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // Give back anything that wasn't sold
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack s = box.removeItemNoUpdate(i);
            if (!s.isEmpty()) player.getInventory().placeItemBackInInventory(s);
        }
        for (int i = ITEM_SLOTS; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
    }
}
