package com.iikka.cloak.mixin;

import com.iikka.cloak.CloakMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** An invisible player is never sent to other players at all: no body, armor, items, name or sounds. */
@Mixin(Entity.class)
public abstract class HideFromPlayersMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void cloak$hide(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (self instanceof ServerPlayer p && p != viewer && CloakMod.isHidden(p)) cir.setReturnValue(false);
    }
}
