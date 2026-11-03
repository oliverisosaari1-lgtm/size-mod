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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** "Your Items": everything you have listed. Click one to take it back. */
public class YourItemsMenu extends ChestMenu {
    private static final int BACK = 45, HELP = 49;
    private final SimpleContainer box;
    private List<Data.Listing> mine;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new YourItemsMenu(id, inv, new SimpleContainer(54), (ServerPlayer) p),
            Component.literal("Your Items")));
    }

    private YourItemsMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        fill(player);
    }

    private void fill(ServerPlayer player) {
        for (int i = 0; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
        mine = Listings.of(player);
        for (int i = 0; i < Math.min(45, mine.size()); i++) {
            box.setItem(i, Listings.display(SmpMod.server(), mine.get(i), "Click to take back"));
        }
        box.setItem(BACK, Gui.button(Items.ARROW, "Back", ChatFormatting.WHITE, "Back to the Auction"));
        box.setItem(HELP, Gui.button(Items.BOOK, "How to sell", ChatFormatting.WHITE,
            "Click an item in your inventory", "then type the price in chat", "Example: 10k",
            "You have " + mine.size() + "/" + Listings.MAX_PER_PLAYER + " items listed"));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        if (slot >= 0 && slot < 54) {
            if (slot == BACK) { ShopMenu.open(player, null, false, 2, 0); return; }
            if (slot < 45 && slot < mine.size()) Listings.cancel(player, mine.get(slot));
            fill(player);
            sendAllDataToRemote();
            return;
        }
        // Clicking an item in your own inventory puts it up for sale
        if (slot >= 54 && slot < slots.size() && type == ContainerInput.PICKUP) {
            var s = slots.get(slot);
            if (s.hasItem() && s.container == player.getInventory()) { Listings.askPrice(player, s.getContainerSlot()); return; }
        }
        sendAllDataToRemote();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
