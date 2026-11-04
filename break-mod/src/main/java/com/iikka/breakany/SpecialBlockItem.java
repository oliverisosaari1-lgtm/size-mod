package com.iikka.breakany;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** A block that normally has no item, like water or a nether portal. Right-click to place it. */
public class SpecialBlockItem extends Item {
    public SpecialBlockItem(Properties p) {
        super(p);
    }

    public static ItemStack of(Block block) {
        ItemStack s = new ItemStack(BreakMod.SPECIAL);
        CompoundTag tag = new CompoundTag();
        String id = BuiltInRegistries.BLOCK.getKey(block).toString();
        tag.putString("block", id);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        s.set(DataComponents.ITEM_NAME, Component.empty().append(block.getName()).append(" Block"));
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        String model = switch (path) {
            case "water", "bubble_column" -> "water_block";
            case "lava" -> "lava_block";
            case "nether_portal" -> "portal_block";
            case "end_portal", "end_gateway" -> "end_portal_block";
            case "fire" -> "fire_block";
            case "soul_fire" -> "soul_fire_block";
            default -> "block";
        };
        s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("breakanything", model));
        return s;
    }

    private static Block blockOf(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Blocks.AIR;
        String id = data.copyTag().getStringOr("block", "");
        if (id.isEmpty()) return Blocks.AIR;
        return BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Block block = blockOf(ctx.getItemInHand());
        if (block == Blocks.AIR) return InteractionResult.FAIL;
        BlockPos pos = ctx.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.relative(ctx.getClickedFace());
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1f, 1f);
            if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }
}
