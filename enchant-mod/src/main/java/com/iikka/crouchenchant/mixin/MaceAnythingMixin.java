package com.iikka.crouchenchant.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Any item smashes like a mace when you hit while falling. */
@Mixin(Item.class)
public abstract class MaceAnythingMixin {
    @Inject(method = "getAttackDamageBonus", at = @At("RETURN"), cancellable = true)
    private void ce$smash(Entity target, float damage, DamageSource source, CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof MaceItem) return;
        if (!(source.getEntity() instanceof ServerPlayer p) || p.isFallFlying()) return;
        double h = p.fallDistance;
        if (h <= 1.5) return;
        double bonus = h <= 3 ? 4 * h : h <= 8 ? 12 + 2 * (h - 3) : 22 + (h - 8);
        cir.setReturnValue(cir.getReturnValue() + (float) bonus);
        p.fallDistance = 0;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.MACE_SMASH_GROUND_HEAVY, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
    }
}
