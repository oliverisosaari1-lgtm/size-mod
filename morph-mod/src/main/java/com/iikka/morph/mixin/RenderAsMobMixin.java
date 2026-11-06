package com.iikka.morph.mixin;

import com.iikka.morph.MorphClient;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A morphed player is drawn as the mob itself: same spot, same moves. No extra entity in the world. */
@Mixin(EntityRenderDispatcher.class)
public abstract class RenderAsMobMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void morph$asMob(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        if (!(entity instanceof Player player)) return;
        LivingEntity mob = MorphClient.bodyFor(player);
        if (mob == null) return;
        try {
            cir.setReturnValue(((EntityRenderDispatcher) (Object) this).extractEntity(mob, partialTick));
        } catch (Throwable t) {
            // if a mob can't be drawn, show the normal player instead of crashing
        }
    }
}
