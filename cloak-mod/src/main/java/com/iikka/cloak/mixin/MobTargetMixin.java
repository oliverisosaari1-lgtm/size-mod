package com.iikka.cloak.mixin;

import com.iikka.cloak.CloakMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mobs can't pick an invisible player as a target. */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void cloak$noTarget(LivingEntity target, CallbackInfo ci) {
        if (target instanceof ServerPlayer p && CloakMod.isHidden(p)) ci.cancel();
    }
}
