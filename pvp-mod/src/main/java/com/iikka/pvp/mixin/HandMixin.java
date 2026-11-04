package com.iikka.pvp.mixin;

import com.iikka.pvp.Config;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Moves, turns and resizes your hand and held item, from the PvP menu settings.
@Mixin(ItemInHandRenderer.class)
public abstract class HandMixin {
    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
    private void pvpmenu$viewmodel(CallbackInfo ci, @Local(argsOnly = true) PoseStack pose, @Local(argsOnly = true) InteractionHand hand) {
        Config c = Config.INSTANCE;
        float side = hand == InteractionHand.MAIN_HAND ? 1 : -1; // mirror for the other hand
        pose.translate(c.x * side, c.y, c.z);
        pose.mulPose(Axis.XP.rotationDegrees((float) c.rotX));
        pose.mulPose(Axis.YP.rotationDegrees((float) c.rotY * side));
        pose.mulPose(Axis.ZP.rotationDegrees((float) c.rotZ * side));
        float s = (float) c.scale;
        pose.scale(s, s, s);
    }
}
