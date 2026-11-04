package com.iikka.smp.mixin;

import com.iikka.smp.SmpClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While zooming, the scroll wheel zooms in and out instead of changing the hotbar slot.
@Mixin(MouseHandler.class)
public abstract class ZoomScrollMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void donutsmp$zoomScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (SmpClient.zooming()) {
            SmpClient.scrollZoom(yOffset);
            ci.cancel();
        }
    }
}
