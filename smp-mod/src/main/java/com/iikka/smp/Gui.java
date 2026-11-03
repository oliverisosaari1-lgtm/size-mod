package com.iikka.smp;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class Gui {
    public static ItemStack button(Item item, String name, ChatFormatting color, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withColor(color).withItalic(false)));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Component.literal(l).withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)));
        stack.set(DataComponents.LORE, new ItemLore(lines));
        return stack;
    }

    public static ItemStack filler() {
        return button(Items.GRAY_STAINED_GLASS_PANE, " ", ChatFormatting.GRAY);
    }
}
