package com.iikka.practice.mixin;

import com.iikka.practice.PracticeClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The main menu shows Olkku's picture instead of the spinning world. */
@Mixin(Screen.class)
public abstract class BackgroundMixin {
    @Inject(method = "renderPanorama", at = @At("HEAD"), cancellable = true, require = 0)
    private void olkku$background(GuiGraphics g, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof TitleScreen) && !(self instanceof com.iikka.practice.PracticeScreens.BotScreen) && !(self instanceof com.iikka.practice.PracticeScreens.DuelScreen)) return;
        g.blit(RenderPipelines.GUI_TEXTURED, PracticeClient.BACKGROUND, 0, 0, 0, 0, self.width, self.height, self.width, self.height);
        ci.cancel();
    }
}
