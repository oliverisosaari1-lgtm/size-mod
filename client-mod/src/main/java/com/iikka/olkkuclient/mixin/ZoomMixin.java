package com.iikka.olkkuclient.mixin;

import com.iikka.olkkuclient.OlkkuClient;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hold C to zoom. */
@Mixin(Camera.class)
public class ZoomMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void olkku$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (OlkkuClient.zooming()) cir.setReturnValue(cir.getReturnValueF() / 4f);
    }
}
