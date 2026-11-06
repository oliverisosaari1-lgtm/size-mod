package com.iikka.crouchenchant.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Any item mines everything like a diamond pickaxe/axe/shovel/hoe, and blocks drop like with the right tool. */
@Mixin(ItemStack.class)
public abstract class ToolAnythingMixin {
    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void ce$speed(BlockState state, CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValue() < 8.0f) cir.setReturnValue(8.0f);
    }

    @Inject(method = "isCorrectToolForDrops", at = @At("HEAD"), cancellable = true)
    private void ce$drops(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
