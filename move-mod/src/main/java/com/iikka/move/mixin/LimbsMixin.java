package com.iikka.move.mixin;

import com.iikka.move.Anim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Arm and leg movement for every animation (armor follows too). */
@Mixin(HumanoidModel.class)
public class LimbsMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void move$limbs(HumanoidRenderState hs, CallbackInfo ci) {
        if (!(hs instanceof AvatarRenderState s)) return;
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        float t;
        if ((t = Anim.progress(s.id, Anim.FLIP)) >= 0) {
            float k = Anim.bump(t);
            m.rightLeg.xRot = -1.5f * k; m.leftLeg.xRot = -1.5f * k;
            m.rightArm.xRot = -1.1f * k; m.leftArm.xRot = -1.1f * k;
            m.rightArm.zRot = 0.25f * k; m.leftArm.zRot = -0.25f * k;
        }
        if ((t = Anim.progress(s.id, Anim.DASH)) >= 0) {
            float k = Anim.bump(t);
            m.rightArm.xRot = 1.3f * k; m.leftArm.xRot = 1.3f * k;
            m.rightArm.zRot = 0.35f * k; m.leftArm.zRot = -0.35f * k;
            m.rightLeg.xRot = -0.7f * k; m.leftLeg.xRot = 0.6f * k;
        }
        float sec;
        if ((sec = Anim.seconds(s.id, Anim.WAVE)) >= 0) {
            float in = Math.min(1, sec * 4) * Math.min(1, (2.4f - sec) * 4);
            m.rightArm.xRot = -2.7f * in;
            m.rightArm.yRot = 0;
            m.rightArm.zRot = (0.15f + (float) Math.sin(sec * 11) * 0.45f) * in;
        }
        if ((sec = Anim.seconds(s.id, Anim.CLAP)) >= 0) {
            float in = Math.min(1, sec * 4) * Math.min(1, (2.2f - sec) * 4);
            float c = (float) Math.abs(Math.sin(sec * 9));
            m.rightArm.xRot = -1.35f * in; m.leftArm.xRot = -1.35f * in;
            m.rightArm.yRot = (-0.15f - 0.5f * c) * in; m.leftArm.yRot = (0.15f + 0.5f * c) * in;
            m.rightArm.zRot = 0; m.leftArm.zRot = 0;
        }
        if ((sec = Anim.seconds(s.id, Anim.BOW)) >= 0) {
            float in = Math.min(1, sec * 3) * Math.min(1, (1.6f - sec) * 3);
            m.rightArm.xRot = 0.35f * in; m.leftArm.xRot = 0.35f * in;
            m.head.xRot = 0.5f * in;
        }
        if ((sec = Anim.seconds(s.id, Anim.CHEER)) >= 0) {
            float in = Math.min(1, sec * 4) * Math.min(1, (2.4f - sec) * 4);
            float w = (float) Math.sin(sec * 10);
            m.rightArm.xRot = (-2.8f + w * 0.2f) * in; m.leftArm.xRot = (-2.8f - w * 0.2f) * in;
            m.rightArm.zRot = 0.45f * in; m.leftArm.zRot = -0.45f * in;
        }
        if ((sec = Anim.seconds(s.id, Anim.DANCE)) >= 0) {
            float b = sec * 7.5f;
            float a = (float) Math.sin(b), c = (float) Math.cos(b);
            m.rightArm.xRot = -1.6f + a * 1.3f; m.leftArm.xRot = -1.6f - a * 1.3f;
            m.rightArm.zRot = 0.5f + c * 0.3f;  m.leftArm.zRot = -0.5f + c * 0.3f;
            m.rightLeg.xRot = Math.max(0, a) * -0.6f; m.leftLeg.xRot = Math.max(0, -a) * -0.6f;
            m.head.zRot = a * 0.15f;
        }
    }
}
