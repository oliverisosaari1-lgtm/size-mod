package com.iikka.smp.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Shows our own picture behind the main menu instead of the spinning panorama.
@Mixin(Screen.class)
public abstract class TitleBackgroundMixin {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath("donutsmp", "textures/gui/title_background.png");
    private static final float IMAGE_RATIO = 1920f / 1080f;

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true, require = 0)
    private void donutsmp$titleBackground(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
        if (!((Object) this instanceof TitleScreen)) return;
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        // Fill the screen without stretching: crop the sides or top/bottom
        float screenRatio = (float) w / h;
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (screenRatio > IMAGE_RATIO) {
            float visible = IMAGE_RATIO / screenRatio;
            v0 = (1 - visible) / 2; v1 = 1 - v0;
        } else {
            float visible = screenRatio / IMAGE_RATIO;
            u0 = (1 - visible) / 2; u1 = 1 - u0;
        }
        graphics.blit(BACKGROUND, 0, 0, w, h, u0, u1, v0, v1);
        ci.cancel();
    }
}
