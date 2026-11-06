package com.iikka.morph.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// In first person, don't draw your own disguise mob (it would block your view). In third person you see it.
@Mixin(EntityRenderDispatcher.class)
public abstract class HideOwnDisguiseMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void morph$hideOwn(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity == mc.player || !entity.hasCustomName()) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;
        if (entity.getCustomName().getString().equals(mc.player.getName().getString()) && entity.distanceToSqr(mc.player) < 4) cir.setReturnValue(false);
    }
}
