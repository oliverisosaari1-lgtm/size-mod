package com.iikka.move.mixin;

import com.iikka.move.Anim;
import com.iikka.move.MoveMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lying pose for /lay, plus the flip and dash lean. */
@Mixin(LivingEntityRenderer.class)
public class BodyMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
        at = @At("HEAD"))
    private void move$lay(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector c, CameraRenderState cam, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState a)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(a.id);
        if (e == null || !(e.getVehicle() instanceof Display.BlockDisplay seat)) return;
        if (seat.getCustomName() == null || !MoveMod.LAY_NAME.equals(seat.getCustomName().getString())) return;
        a.pose = Pose.SLEEPING;
        a.nameTag = null;
        a.bedOrientation = null;
        a.isPassenger = false;
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
        at = @At("TAIL"))
    private void move$spin(LivingEntityRenderState state, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState a)) return;
        float flip = Anim.progress(a.id, Anim.FLIP);
        if (flip >= 0) {
            float mid = 0.95f * a.scale;
            pose.translate(0, mid, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-360f * Anim.easeInOut(flip)));
            pose.translate(0, -mid, 0);
        }
        float dash = Anim.progress(a.id, Anim.DASH);
        if (dash >= 0) pose.mulPose(Axis.XP.rotationDegrees(-28f * Anim.bump(dash)));
        float sec;
        if ((sec = Anim.seconds(a.id, Anim.BOW)) >= 0) {
            float in = Math.min(1, sec * 3) * Math.min(1, (1.6f - sec) * 3);
            pose.translate(0, 0.75f * a.scale, 0);
            pose.mulPose(Axis.XP.rotationDegrees(-45f * in));
            pose.translate(0, -0.75f * a.scale, 0);
        }
        if ((sec = Anim.seconds(a.id, Anim.DANCE)) >= 0) {
            float b = sec * 7.5f;
            pose.translate(0, Math.abs(Math.sin(b)) * 0.12f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(b) * 9f));
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(b * 0.5f) * 20f));
        }
        if ((sec = Anim.seconds(a.id, Anim.CHEER)) >= 0)
            pose.translate(0, Math.abs(Math.sin(sec * 10)) * 0.15f * Math.min(1, (2.4f - sec) * 4), 0);
    }
}
