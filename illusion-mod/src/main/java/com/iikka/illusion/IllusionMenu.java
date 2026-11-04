package com.iikka.illusion;

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

import java.util.List;

/** /illusion: click one to pick it. */
public class IllusionMenu extends ChestMenu {
    private static final int OFF = 31;
    private final SimpleContainer box;

    public static void open(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider(
            (id, inv, p) -> new IllusionMenu(id, inv, new SimpleContainer(36), (ServerPlayer) p),
            Component.literal("Illusions")));
    }

    private IllusionMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer player) {
        super(MenuType.GENERIC_9x4, id, inv, box, 4);
        this.box = box;
        fill(player);
    }

    private static int slotOf(int i) {
        return 10 + (i / 6) * 9 + (i % 6) + (i % 6 >= 3 ? 1 : 0) - 0; // two rows of 6
    }

    private static ItemStack button(Item item, String name, ChatFormatting color, boolean glow, String... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(st -> st.withColor(color).withItalic(false)));
        s.set(DataComponents.LORE, new ItemLore(java.util.Arrays.stream(lore)
            .map(l -> (Component) Component.literal(l).withStyle(st -> st.withColor(ChatFormatting.GRAY).withItalic(false))).toList()));
        if (glow) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return s;
    }

    private void fill(ServerPlayer p) {
        Illusion mine = IllusionMod.get(p);
        for (int i = 0; i < 36; i++) box.setItem(i, button(Items.BLACK_STAINED_GLASS_PANE, " ", ChatFormatting.GRAY, false));
        Illusion[] all = Illusion.values();
        for (int i = 0; i < all.length; i++) {
            Illusion il = all[i];
            boolean on = il == mine;
            box.setItem(slotOf(i), button(il.icon, il.name, on ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE, on,
                il.info, on ? "Selected!" : "Click to use"));
        }
        box.setItem(OFF, button(Items.BARRIER, "No Illusion", ChatFormatting.RED, mine == null, "Turn illusions off"));
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player p) {
        if (!(p instanceof ServerPlayer player)) return;
        if (slot >= 0 && slot < 36) {
            Illusion[] all = Illusion.values();
            for (int i = 0; i < all.length; i++) {
                if (slotOf(i) == slot) {
                    IllusionMod.set(player, all[i]);
                    player.sendSystemMessage(Component.literal("Illusion: " + all[i].name + "! Shoot a bow to see it.").withStyle(ChatFormatting.LIGHT_PURPLE));
                    all[i].hit((net.minecraft.server.level.ServerLevel) player.level(), player.position().add(0, 1, 0)); // a little preview
                }
            }
            if (slot == OFF) {
                IllusionMod.set(player, null);
                player.sendSystemMessage(Component.literal("Illusions off.").withStyle(ChatFormatting.GRAY));
            }
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
