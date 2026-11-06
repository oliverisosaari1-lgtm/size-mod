package com.iikka.morph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

/** The morph menu: every mob you've killed. Click one to turn into it. */
public class MorphMenu extends ChestMenu {
    private static final int PER_PAGE = 45, PREV = 45, UNMORPH = 49, NEXT = 53;
    private final SimpleContainer box;
    private final List<String> mobs;
    private int page = 0;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new MorphMenu(id, inv, new SimpleContainer(54), (ServerPlayer) p),
            Component.literal("Morph")));
    }

    private MorphMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        this.mobs = new ArrayList<>(MorphMod.unlocked(player));
        fill(player);
    }

    static ItemStack button(Item item, Component name, ChatFormatting color, boolean glow, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(st -> st.withColor(color).withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Component.literal(l).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false)));
        s.set(DataComponents.LORE, new ItemLore(lines));
        if (glow) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private static Item egg(String id) {
        Identifier key = Identifier.parse(id);
        Item egg = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(key.getNamespace(), key.getPath() + "_spawn_egg"));
        return egg == Items.AIR ? Items.NAME_TAG : egg;
    }

    private void fill(ServerPlayer p) {
        for (int i = 0; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
        for (int i = 45; i < 54; i++) box.setItem(i, button(Items.BLACK_STAINED_GLASS_PANE, Component.literal(" "), ChatFormatting.GRAY, false));
        String current = MorphMod.current(p);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= mobs.size()) break;
            String id = mobs.get(idx);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
            boolean on = id.equals(current);
            box.setItem(i, button(egg(id), type.getDescription(), on ? ChatFormatting.GREEN : ChatFormatting.YELLOW, on,
                on ? "You are this now!" : "Click to turn into it"));
        }
        if (mobs.isEmpty()) box.setItem(22, button(Items.BOOK, Component.literal("No mobs yet"), ChatFormatting.WHITE, false, "Kill a mob to unlock it!"));
        if (page > 0) box.setItem(PREV, button(Items.ARROW, Component.literal("Previous Page"), ChatFormatting.WHITE, false));
        if ((page + 1) * PER_PAGE < mobs.size()) box.setItem(NEXT, button(Items.ARROW, Component.literal("Next Page"), ChatFormatting.WHITE, false));
        box.setItem(UNMORPH, button(Items.BARRIER, Component.literal("Turn back to normal"), ChatFormatting.RED, false, "Be yourself again"));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player pl) {
        if (!(pl instanceof ServerPlayer p)) return;
        if (slot >= 0 && slot < 54) {
            if (slot == PREV && page > 0) page--;
            else if (slot == NEXT && (page + 1) * PER_PAGE < mobs.size()) page++;
            else if (slot == UNMORPH) { MorphMod.unmorph(p, true); p.closeContainer(); return; }
            else if (slot < PER_PAGE && page * PER_PAGE + slot < mobs.size()) {
                MorphMod.morph(p, mobs.get(page * PER_PAGE + slot));
                p.closeContainer();
                return;
            }
            fill(p);
            sendAllDataToRemote();
            return;
        }
        if (type == ClickType.QUICK_MOVE) return;
        super.clicked(slot, button, type, pl);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
