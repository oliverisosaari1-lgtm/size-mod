package com.iikka.sizemod.mixin;

import com.iikka.sizemod.SizeMod;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Minecraft normally caps the scale attribute between 0.0625 and 16. Let it go from 0.01 to 100.
@Mixin(RangedAttribute.class)
public abstract class RangedAttributeMixin {
    @Inject(method = "sanitizeValue", at = @At("HEAD"), cancellable = true)
    private void sizemod$widenScale(double value, CallbackInfoReturnable<Double> cir) {
        if ("attribute.name.scale".equals(((Attribute) (Object) this).getDescriptionId())) {
            cir.setReturnValue(Double.isNaN(value) ? 1.0 : Mth.clamp(value, SizeMod.TINIEST_SCALE, SizeMod.MAX_SIZE));
        }
    }
}
