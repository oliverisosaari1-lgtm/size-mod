package com.iikka.smp.mixin;

import com.iikka.smp.SmpClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Holding the zoom key (C) makes the field of view smaller, so you see further.
@Mixin(Camera.class)
public abstract class ZoomMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void donutsmp$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (SmpClient.zooming()) cir.setReturnValue(cir.getReturnValueF() / SmpClient.zoomAmount());
    }
}
