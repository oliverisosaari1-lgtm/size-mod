package com.iikka.crouchenchant;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/** L menu: one book per enchant on your held item. Click one to take only that enchant off (you get it as a book). */
public class DetachMenu extends ChestMenu {
    private final SimpleContainer box;
    private final List<Holder<Enchantment>> list = new ArrayList<>();

    public static void open(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (stack.isEmpty() || stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty()) {
            p.displayClientMessage(Component.literal("This item has no enchants.").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new DetachMenu(id, inv, new SimpleContainer(54), (ServerPlayer) pl),
            Component.literal("Pick an enchant to take off")));
    }

    private DetachMenu(int id, Inventory inv, SimpleContainer box, ServerPlayer p) {
        super(MenuType.GENERIC_9x6, id, inv, box, 6);
        this.box = box;
        fill(p);
    }

    private void fill(ServerPlayer p) {
        list.clear();
        for (int i = 0; i < 54; i++) box.setItem(i, ItemStack.EMPTY);
        ItemEnchantments ench = p.getMainHandItem().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var e : ench.entrySet()) {
            if (list.size() >= 54) break;
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            ItemEnchantments.Mutable one = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            one.set(e.getKey(), e.getIntValue());
            book.set(DataComponents.STORED_ENCHANTMENTS, one.toImmutable());
            book.set(DataComponents.CUSTOM_NAME, Enchantment.getFullname(e.getKey(), e.getIntValue()).copy().withStyle(st -> st.withItalic(false).withColor(ChatFormatting.AQUA)));
            box.setItem(list.size(), book);
            list.add(e.getKey());
        }
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player pl) {
        if (!(pl instanceof ServerPlayer p)) return;
        if (slot >= 0 && slot < list.size()) {
            Holder<Enchantment> pick = list.get(slot);
            ItemStack stack = p.getMainHandItem();
            int[] lvl = new int[1];
            EnchantmentHelper.updateEnchantments(stack, m -> { lvl[0] = m.getLevel(pick); m.set(pick, 0); });
            if (lvl[0] > 0) {
                ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantments.Mutable one = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                one.set(pick, lvl[0]);
                book.set(DataComponents.STORED_ENCHANTMENTS, one.toImmutable());
                if (!p.getInventory().add(book)) p.drop(book, false);
                p.displayClientMessage(Component.literal("Took off ").append(Enchantment.getFullname(pick, lvl[0])).withStyle(ChatFormatting.AQUA), true);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 1f);
            }
            p.closeContainer();
        }
        // every other click does nothing (so items can't be moved while the menu is open)
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
