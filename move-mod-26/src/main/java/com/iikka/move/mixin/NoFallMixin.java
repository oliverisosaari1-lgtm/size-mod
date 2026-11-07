package com.iikka.move.mixin;

import com.iikka.move.MoveMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No fall damage at all after a dash or double jump, until you land. */
@Mixin(Player.class)
public class NoFallMixin {
    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    private void move$noFall(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer p && MoveMod.isFallSafe(p)) {
            p.resetFallDistance();
            cir.setReturnValue(false);
        }
    }
}
