package com.iikka.practice.mixin;

import com.iikka.practice.PracticeClient;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hold C to zoom in. */
@Mixin(GameRenderer.class)
public abstract class ZoomMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void olkku$zoom(Camera camera, float partialTick, boolean useFov, CallbackInfoReturnable<Float> cir) {
        if (PracticeClient.zoom != null && PracticeClient.zoom.isDown()) cir.setReturnValue(cir.getReturnValue() * 0.25f);
    }
}
