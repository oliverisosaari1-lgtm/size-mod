package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** /ah: a 6-row chest. Top 5 rows are items for sale, bottom row is page buttons and your money. */
public class ShopMenu extends ChestMenu {
    private static final int PER_PAGE = 45;
    private final SimpleContainer box;
    private final List<Item> items = new ArrayList<>(Prices.SHOP.keySet());
    private int page = 0;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new ShopMenu(id, inv, new SimpleContainer(54), (ServerPlayer) p),
            Component.literal("Auction House")));
    }

    private ShopMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        fill(player);
    }

    private int pages() {
        return Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void fill(ServerPlayer player) {
        for (int i = 0; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= items.size()) break;
            Item item = items.get(idx);
            ItemStack shown = new ItemStack(item);
            List<Component> lore = new ArrayList<>();
            lore.add(line("Price: " + Prices.money(Prices.price(item)), ChatFormatting.GREEN));
            lore.add(line("Left click: buy 1", ChatFormatting.GRAY));
            lore.add(line("Right click: buy " + item.getDefaultMaxStackSize(), ChatFormatting.GRAY));
            shown.set(DataComponents.LORE, new ItemLore(lore));
            box.setItem(i, shown);
        }
        for (int i = 45; i < 54; i++) box.setItem(i, Gui.filler());
        if (page > 0) box.setItem(45, Gui.button(Items.ARROW, "Previous page", ChatFormatting.YELLOW));
        if (page < pages() - 1) box.setItem(53, Gui.button(Items.ARROW, "Next page", ChatFormatting.YELLOW));
        box.setItem(49, Gui.button(Items.GOLD_INGOT, "Your money: " + Prices.money(SmpMod.money(player)), ChatFormatting.GOLD,
            "Page " + (page + 1) + " of " + pages()));
    }

    private static Component line(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(s -> s.withColor(color).withItalic(false));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        if (slot >= 0 && slot < 54) {
            if (slot == 45 && page > 0) page--;
            else if (slot == 53 && page < pages() - 1) page++;
            else if (slot < PER_PAGE && (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE)) {
                int idx = page * PER_PAGE + slot;
                if (idx < items.size()) buy(player, items.get(idx), button == 1 ? items.get(idx).getDefaultMaxStackSize() : 1);
            }
            fill(player);
            sendAllDataToRemote();
            return;
        }
        if (type == ContainerInput.QUICK_MOVE) return; // don't shift-click your stuff into the shop
        super.clicked(slot, button, type, p);
    }

    private void buy(ServerPlayer player, Item item, int count) {
        long cost = Prices.price(item) * count;
        if (SmpMod.money(player) < cost) {
            player.sendSystemMessage(Component.literal("You need " + Prices.money(cost) + " for that!").withStyle(ChatFormatting.RED));
            return;
        }
        SmpMod.addMoney(player, -cost);
        ItemStack stack = new ItemStack(item, count);
        Gui.give(player, stack);
        player.sendSystemMessage(Component.literal("Bought " + count + "x " + item.getName(new ItemStack(item)).getString()
            + " for " + Prices.money(cost)).withStyle(ChatFormatting.GREEN));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
