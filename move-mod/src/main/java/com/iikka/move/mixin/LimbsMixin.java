package com.iikka.move.mixin;

import com.iikka.move.Anim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tuck the legs during a flip; sweep the arms back during a dash. */
@Mixin(PlayerModel.class)
public class LimbsMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void move$limbs(AvatarRenderState s, CallbackInfo ci) {
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        float flip = Anim.progress(s.id, Anim.FLIP);
        if (flip >= 0) {
            float k = Anim.bump(flip);
            m.rightLeg.xRot = -1.5f * k; m.leftLeg.xRot = -1.5f * k;
            m.rightArm.xRot = -1.1f * k; m.leftArm.xRot = -1.1f * k;
            m.rightArm.zRot = 0.25f * k; m.leftArm.zRot = -0.25f * k;
        }
        float dash = Anim.progress(s.id, Anim.DASH);
        if (dash >= 0) {
            float k = Anim.bump(dash);
            m.rightArm.xRot = 1.3f * k; m.leftArm.xRot = 1.3f * k;
            m.rightArm.zRot = 0.35f * k; m.leftArm.zRot = -0.35f * k;
            m.rightLeg.xRot = -0.7f * k; m.leftLeg.xRot = 0.6f * k;
        }
    }
}
