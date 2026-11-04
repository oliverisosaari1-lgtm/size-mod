package com.iikka.breakany;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;

public class BreakMod implements ModInitializer {
    public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("breakanything", "block"));
    public static Item SPECIAL;

    @Override
    public void onInitialize() {
        SPECIAL = Registry.register(BuiltInRegistries.ITEM, KEY, new SpecialBlockItem(new Item.Properties().setId(KEY)));

        // Blocks that normally drop nothing (bedrock, water, portals...) drop themselves
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide() || player.isCreative()) return;
            Block block = state.getBlock();
            boolean special = block instanceof LiquidBlock || block.defaultDestroyTime() < 0;
            if (!special) return;
            Item item = block.asItem();
            ItemStack drop = item != Items.AIR ? new ItemStack(item) : SpecialBlockItem.of(block);
            Block.popResource(level, pos, drop);
        });
    }
}
