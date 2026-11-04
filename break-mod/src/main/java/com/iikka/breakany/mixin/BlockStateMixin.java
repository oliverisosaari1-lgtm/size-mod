package com.iikka.breakany.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateMixin {
    // Unbreakable blocks (bedrock, portals, barriers...) can be broken. Water and lava too.
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void breakanything$speed(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        if (self.getBlock() instanceof LiquidBlock) cir.setReturnValue(0.5f);
        else if (cir.getReturnValueF() < 0) cir.setReturnValue(3.0f);
    }

    // Water and lava get an outline so you can aim at them and break them.
    @Inject(method = "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        at = @At("HEAD"), cancellable = true)
    private void breakanything$shape(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        if (self.getBlock() instanceof LiquidBlock) cir.setReturnValue(Shapes.block());
    }
}
