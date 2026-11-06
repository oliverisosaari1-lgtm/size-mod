package com.iikka.crouchenchant;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws any non-armor item you wear (dirt as boots, a potato as a chestplate...) on your body. */
public class WornItemLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ItemModelResolver resolver;
    private final ItemStackRenderState item = new ItemStackRenderState();

    public WornItemLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver resolver) {
        super(parent);
        this.resolver = resolver;
    }

    private static boolean custom(ItemStack s) {
        if (s.isEmpty()) return false;
        var eq = s.get(DataComponents.EQUIPPABLE);
        return eq == null || eq.assetId().isEmpty(); // real armor already draws itself
    }

    private void draw(PoseStack pose, SubmitNodeCollector out, int light, ItemStack stack, ModelPart part, float y, float scale) {
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.translate(0, y, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(scale, -scale, -scale);
        resolver.updateForTopItem(item, stack, ItemDisplayContext.HEAD, null, null, 0);
        item.submit(pose, out, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, AvatarRenderState s, float yRot, float xRot) {
        try {
            PlayerModel m = getParentModel();
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level == null || !(level.getEntity(s.id) instanceof net.minecraft.world.entity.LivingEntity e)) return;
            ItemStack chest = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
            ItemStack legs = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS);
            ItemStack feet = e.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
            if (custom(chest)) draw(pose, out, light, chest, m.body, 0.375f, 0.55f);
            if (custom(legs)) { draw(pose, out, light, legs, m.rightLeg, 0.3f, 0.35f); draw(pose, out, light, legs, m.leftLeg, 0.3f, 0.35f); }
            if (custom(feet)) { draw(pose, out, light, feet, m.rightLeg, 0.62f, 0.3f); draw(pose, out, light, feet, m.leftLeg, 0.62f, 0.3f); }
        } catch (Throwable ignored) { }
    }
}
