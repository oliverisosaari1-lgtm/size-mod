package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /ah: "Auction (Page N)". Top 5 rows are items, bottom row has Filter, Quick Buy, Search, Your Items and page arrows. */
public class ShopMenu extends ChestMenu {
    private static final int PER_PAGE = 45;
    private static final String[] MODES = {"Lowest Price", "Highest Price", "Recently Listed"};
    private static final int PREV = 45, FILTER = 47, QUICK = 48, SEARCH = 49, INFO = 50, MINE = 51, NEXT = 53;

    private final SimpleContainer box;
    private final List<Item> items;
    private final int page, mode;
    private final String search;
    private final boolean quick;

    public static void open(ServerPlayer player, String search, boolean quick, int mode, int page) {
        List<Item> list = list(search, quick, mode);
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        String title = (quick ? "Quick Buy" : "Auction") + " (Page " + (pg + 1) + ")";
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new ShopMenu(id, inv, new SimpleContainer(54), list, search, quick, mode, pg),
            Component.literal(title)));
    }

    private static List<Item> list(String search, boolean quick, int mode) {
        List<Item> base = new ArrayList<>(quick ? new ArrayList<>(Prices.SHOP.keySet()) : Prices.allItems());
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase(Locale.ROOT).trim();
            base.removeIf(item -> !new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                && !BuiltInRegistries.ITEM.getKey(item).getPath().contains(q.replace(' ', '_')));
        }
        if (mode == 0) base.sort(Comparator.comparingLong(Prices::buyPrice));
        else if (mode == 1) base.sort(Comparator.comparingLong(Prices::buyPrice).reversed());
        else java.util.Collections.reverse(base);
        return base;
    }

    private ShopMenu(int id, Inventory inv, SimpleContainer box, List<Item> items, String search, boolean quick, int mode, int page) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        this.items = items;
        this.search = search;
        this.quick = quick;
        this.mode = mode;
        this.page = page;
        fill();
    }

    private int pages() {
        return Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private void fill() {
        for (int i = 0; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= items.size()) break;
            Item item = items.get(idx);
            List<Component> lore = new ArrayList<>();
            lore.add(Prices.priceLine(Prices.buyPrice(item)));
            lore.add(Gui.text("Left click to buy 1", ChatFormatting.GRAY));
            lore.add(Gui.text("Right click to buy " + item.getDefaultMaxStackSize(), ChatFormatting.GRAY));
            box.setItem(i, Gui.withLore(new ItemStack(item), null, lore));
        }
        if (page > 0) box.setItem(PREV, Gui.button(Items.ARROW, "Previous Page", ChatFormatting.WHITE));
        if (page < pages() - 1) box.setItem(NEXT, Gui.button(Items.ARROW, "Next Page", ChatFormatting.WHITE));

        List<Component> filter = new ArrayList<>();
        filter.add(Component.literal("Click to change").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(true)));
        filter.add(Component.empty());
        for (int m = 0; m < MODES.length; m++) {
            filter.add(Gui.text("• " + MODES[m], m == mode ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        box.setItem(FILTER, Gui.withLore(new ItemStack(Items.HOPPER), Gui.text("Filter", ChatFormatting.WHITE), filter));
        box.setItem(QUICK, Gui.button(Items.ENDER_CHEST, quick ? "All Items" : "Quick Buy", ChatFormatting.WHITE, "Click to view"));
        box.setItem(SEARCH, Gui.button(Items.ANVIL, "Search", ChatFormatting.WHITE,
            search == null || search.isBlank() ? "Click to search" : "Searching: " + search, "Click to search"));
        box.setItem(INFO, Gui.button(Items.OAK_SIGN, "Auction House", ChatFormatting.WHITE,
            "Every item in the game is here!", "Left click: buy 1", "Right click: buy a stack"));
        box.setItem(MINE, Gui.button(Items.CHEST, "Your Items", ChatFormatting.WHITE, "Click to view"));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        if (slot >= 0 && slot < 54) {
            if (slot == PREV && page > 0) { open(player, search, quick, mode, page - 1); return; }
            if (slot == NEXT && page < pages() - 1) { open(player, search, quick, mode, page + 1); return; }
            if (slot == FILTER) { open(player, search, quick, (mode + 1) % MODES.length, 0); return; }
            if (slot == QUICK) { open(player, null, !quick, mode, 0); return; }
            if (slot == SEARCH) {
                player.closeContainer();
                if (!SmpMod.openScreen(player, "chat", "/ah ")) {
                    player.sendSystemMessage(Component.literal("Type /ah <item name> to search").withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
            if (slot == MINE) {
                player.sendSystemMessage(Component.literal("You have no items listed. Selling to other players is coming soon!").withStyle(ChatFormatting.YELLOW));
            } else if (slot < PER_PAGE && (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE)) {
                int idx = page * PER_PAGE + slot;
                if (idx < items.size()) buy(player, items.get(idx), button == 1 ? items.get(idx).getDefaultMaxStackSize() : 1);
            }
            sendAllDataToRemote();
            return;
        }
        if (type == ContainerInput.QUICK_MOVE) return;
        super.clicked(slot, button, type, p);
    }

    private void buy(ServerPlayer player, Item item, int count) {
        long cost = Prices.buyPrice(item) * count;
        if (SmpMod.money(player) < cost) {
            player.sendSystemMessage(Component.literal("You need " + Prices.money(cost) + " for that!").withStyle(ChatFormatting.RED));
            return;
        }
        SmpMod.addMoney(player, -cost);
        Gui.give(player, new ItemStack(item, count));
        player.sendSystemMessage(Component.literal("Bought " + count + "x " + new ItemStack(item).getHoverName().getString()
            + " for " + Prices.money(cost)).withStyle(ChatFormatting.GREEN));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
