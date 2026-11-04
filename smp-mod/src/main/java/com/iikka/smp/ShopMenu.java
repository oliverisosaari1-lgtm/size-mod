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
    private List<Object> items;
    private int page, mode;
    private String search;
    private boolean quick;

    public static void open(ServerPlayer player, String search, boolean quick, int mode, int page) {
        List<Object> list = list(search, quick, mode);
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.max(0, Math.min(page, pages - 1));
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new ShopMenu(id, inv, new SimpleContainer(54), list, search, quick, mode, pg),
            title(quick, pg)));
    }

    private static Component title(boolean quick, int page) {
        return Component.literal((quick ? "Quick Buy" : "Auction") + " (Page " + (page + 1) + ")");
    }

    /** Change page/filter without closing the menu, so the mouse stays where it is. */
    private void refresh(ServerPlayer player, String search, boolean quick, int mode, int page) {
        this.search = search;
        this.quick = quick;
        this.mode = mode;
        this.items = list(search, quick, mode);
        this.page = Math.max(0, Math.min(page, pages() - 1));
        fill();
        // Re-send the window with the new title. Same window id, and no "close" first, so the cursor doesn't jump.
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundOpenScreenPacket(containerId, MenuType.GENERIC_9x6, title(this.quick, this.page)));
        sendAllDataToRemote();
    }

    private static long priceOf(Object o) {
        return o instanceof Data.Listing l ? l.price : Prices.buyPrice((Item) o);
    }

    /** Short names people type on DonutSMP, like /ah gap */
    private static final java.util.Map<String, String> ALIASES = java.util.Map.ofEntries(
        java.util.Map.entry("gap", "golden_apple"), java.util.Map.entry("gapple", "golden_apple"),
        java.util.Map.entry("egap", "enchanted_golden_apple"), java.util.Map.entry("god apple", "enchanted_golden_apple"),
        java.util.Map.entry("notch apple", "enchanted_golden_apple"),
        java.util.Map.entry("echest", "ender_chest"), java.util.Map.entry("ec", "ender_chest"),
        java.util.Map.entry("pearl", "ender_pearl"), java.util.Map.entry("pearls", "ender_pearl"),
        java.util.Map.entry("xp", "experience_bottle"), java.util.Map.entry("exp", "experience_bottle"), java.util.Map.entry("xp bottle", "experience_bottle"),
        java.util.Map.entry("totem", "totem_of_undying"), java.util.Map.entry("totems", "totem_of_undying"),
        java.util.Map.entry("shulker", "shulker_box"), java.util.Map.entry("shulkers", "shulker_box"),
        java.util.Map.entry("crystal", "end_crystal"), java.util.Map.entry("crystals", "end_crystal"),
        java.util.Map.entry("anchor", "respawn_anchor"), java.util.Map.entry("obby", "obsidian"), java.util.Map.entry("obi", "obsidian"),
        java.util.Map.entry("cobble", "cobblestone"), java.util.Map.entry("ench table", "enchanting_table"),
        java.util.Map.entry("pick", "pickaxe"), java.util.Map.entry("netherite", "netherite"),
        java.util.Map.entry("rockets", "firework_rocket"), java.util.Map.entry("fireworks", "firework_rocket"),
        java.util.Map.entry("bottle", "experience_bottle"), java.util.Map.entry("steak", "cooked_beef"),
        java.util.Map.entry("carrots", "golden_carrot"), java.util.Map.entry("gcarrot", "golden_carrot"),
        java.util.Map.entry("tnt", "tnt"), java.util.Map.entry("spawner", "spawner"), java.util.Map.entry("elytra", "elytra"));

    private static boolean matches(String q, ItemStack stack) {
        String alias = ALIASES.get(q);
        if (alias != null) return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(alias);
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
            || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(q.replace(' ', '_'));
    }

    private static List<Object> list(String search, boolean quick, int mode) {
        String q = search == null || search.isBlank() ? null : search.toLowerCase(Locale.ROOT).trim();
        // Player listings first, then every item in the game
        List<Object> players = new ArrayList<>();
        if (!quick) {
            for (Data.Listing l : SmpMod.DATA.listings) {
                if (q == null || matches(q, Listings.load(SmpMod.server(), l))) players.add(l);
            }
        }
        List<Object> game = new ArrayList<>();
        for (Item item : quick ? new ArrayList<>(Prices.SHOP.keySet()) : Prices.allItems()) {
            if (q == null || matches(q, new ItemStack(item))) game.add(item);
        }
        if (mode == 0) { players.sort(Comparator.comparingLong(ShopMenu::priceOf)); game.sort(Comparator.comparingLong(ShopMenu::priceOf)); }
        else if (mode == 1) { players.sort(Comparator.comparingLong(ShopMenu::priceOf).reversed()); game.sort(Comparator.comparingLong(ShopMenu::priceOf).reversed()); }
        else { players.sort(Comparator.comparingLong((Object o) -> ((Data.Listing) o).time).reversed()); java.util.Collections.reverse(game); }
        players.addAll(game);
        return players;
    }

    private ShopMenu(int id, Inventory inv, SimpleContainer box, List<Object> items, String search, boolean quick, int mode, int page) {
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
            if (items.get(idx) instanceof Data.Listing l) {
                box.setItem(i, Listings.display(SmpMod.server(), l, "Click to buy"));
                continue;
            }
            Item item = (Item) items.get(idx);
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
            "Every item in the game is here!", "Left click: buy 1", "Right click: buy a stack", "Sell your own: /ah sell <price>"));
        box.setItem(MINE, Gui.button(Items.CHEST, "Your Items", ChatFormatting.WHITE, "Click to view", "Sell: hold an item, /ah sell <price>"));
    }

    @Override
    public void clicked(int slot, int button, ContainerInput type, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        if (slot >= 0 && slot < 54) {
            if (slot == PREV && page > 0) { refresh(player, search, quick, mode, page - 1); return; }
            if (slot == NEXT && page < pages() - 1) { refresh(player, search, quick, mode, page + 1); return; }
            if (slot == FILTER) { refresh(player, search, quick, (mode + 1) % MODES.length, 0); return; }
            if (slot == QUICK) { refresh(player, null, !quick, mode, 0); return; }
            if (slot == SEARCH) {
                player.closeContainer();
                if (!SmpMod.openScreen(player, "chat", "/ah ")) {
                    player.sendSystemMessage(Component.literal("Type /ah <item name> to search").withStyle(ChatFormatting.YELLOW));
                }
                return;
            }
            if (slot == MINE) { YourItemsMenu.open(player); return; }
            if (slot < PER_PAGE && (type == ContainerInput.PICKUP || type == ContainerInput.QUICK_MOVE)) {
                int idx = page * PER_PAGE + slot;
                if (idx < items.size()) {
                    Object o = items.get(idx);
                    if (o instanceof Data.Listing l) {
                        Listings.buy(player, l);
                        refresh(player, search, quick, mode, page);
                        return;
                    }
                    Item item = (Item) o;
                    buy(player, item, button == 1 ? item.getDefaultMaxStackSize() : 1);
                }
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
