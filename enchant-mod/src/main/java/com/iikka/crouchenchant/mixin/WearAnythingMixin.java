package com.iikka.crouchenchant.mixin;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Players can put any item in any armor slot. */
@Mixin(LivingEntity.class)
public abstract class WearAnythingMixin {
    @Inject(method = "isEquippableInSlot", at = @At("HEAD"), cancellable = true)
    private void ce$anyArmor(ItemStack stack, EquipmentSlot slot, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player && slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) cir.setReturnValue(true);
    }
}
